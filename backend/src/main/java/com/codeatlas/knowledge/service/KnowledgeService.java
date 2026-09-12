package com.codeatlas.knowledge.service;

import com.codeatlas.ai.config.AiProperties;
import com.codeatlas.ai.provider.ChatProvider;
import com.codeatlas.ai.provider.EmbeddingProvider;
import com.codeatlas.ai.qdrant.QdrantClient;
import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.document.entity.FileChunk;
import com.codeatlas.document.repository.FileChunkRepository;
import com.codeatlas.knowledge.dto.AskAnswerVO;
import com.codeatlas.knowledge.dto.BuildResultVO;
import com.codeatlas.knowledge.dto.CitationVO;
import com.codeatlas.knowledge.dto.IndexStatusVO;
import com.codeatlas.knowledge.dto.SearchResultVO;
import com.codeatlas.project.service.ProjectPermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库服务：向量化入库、检索与 RAG 问答。
 *
 * <p>对应 docs/development.md 第 7 节（Phase 5）：
 * Chunk → Embedding → Qdrant；Question → Embedding → 检索 → Context → LLM → Answer。
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private static final String SYSTEM_PROMPT = """
            你是一个软件项目分析助手。请只根据下面提供的项目资料回答问题。

            规则：
            1. 如果项目资料中没有相关信息，请明确回答"在项目资料中未找到相关依据"，不要凭自己的知识猜测。
            2. 回答时尽量引用具体的文件名或代码位置。
            3. 使用中文回答，保持简洁准确。
            """;

    private final FileChunkRepository chunkRepository;

    private final ProjectPermissionService permissionService;

    private final EmbeddingProvider embeddingProvider;

    private final ChatProvider chatProvider;

    private final QdrantClient qdrantClient;

    private final AiProperties aiProperties;

    public KnowledgeService(FileChunkRepository chunkRepository,
                            ProjectPermissionService permissionService,
                            EmbeddingProvider embeddingProvider,
                            ChatProvider chatProvider,
                            QdrantClient qdrantClient,
                            AiProperties aiProperties) {
        this.chunkRepository = chunkRepository;
        this.permissionService = permissionService;
        this.embeddingProvider = embeddingProvider;
        this.chatProvider = chatProvider;
        this.qdrantClient = qdrantClient;
        this.aiProperties = aiProperties;
    }

    /** 构建知识库：将未索引的 Chunk 向量化并写入 Qdrant。 */
    @Transactional
    public BuildResultVO buildIndex(Long projectId, Long operatorId) {
        permissionService.requireWriter(projectId, operatorId);
        qdrantClient.ensureCollection(embeddingProvider.dimension());

        List<FileChunk> pending = chunkRepository.findByProjectIdAndIndexedFalse(projectId);
        if (pending.isEmpty()) {
            return new BuildResultVO(0, 0, 0);
        }

        int batchSize = Math.max(1, aiProperties.getEmbedding().getBatchSize());
        int indexed = 0;
        int failed = 0;

        for (int i = 0; i < pending.size(); i += batchSize) {
            List<FileChunk> batch = pending.subList(i, Math.min(i + batchSize, pending.size()));
            List<String> texts = batch.stream().map(FileChunk::getContent).toList();

            try {
                List<List<Double>> vectors = embeddingProvider.embedBatch(texts);
                List<QdrantClient.VectorPoint> points = new ArrayList<>();
                for (int j = 0; j < batch.size(); j++) {
                    FileChunk chunk = batch.get(j);
                    points.add(new QdrantClient.VectorPoint(
                            chunk.getId(),
                            vectors.get(j),
                            buildPayload(projectId, chunk)));
                }
                qdrantClient.upsert(points);

                for (FileChunk chunk : batch) {
                    chunk.setIndexed(true);
                }
                chunkRepository.saveAll(batch);
                indexed += batch.size();
            } catch (Exception ex) {
                log.error("知识库构建失败 | projectId={} | batchStart={}", projectId, i, ex);
                failed += batch.size();
            }
        }

        log.info("知识库构建完成 | projectId={} | indexed={} | failed={}", projectId, indexed, failed);
        return new BuildResultVO(pending.size(), indexed, failed);
    }

    /** 索引状态：总块数与待索引块数。 */
    @Transactional(readOnly = true)
    public IndexStatusVO status(Long projectId, Long operatorId) {
        permissionService.requireMember(projectId, operatorId);
        long total = chunkRepository.countByProjectId(projectId);
        long pending = chunkRepository.findByProjectIdAndIndexedFalse(projectId).size();
        return new IndexStatusVO(total, total - pending, pending,
                qdrantClient.getCollection(), embeddingProvider.modelName());
    }

    /** 向量检索：返回最相似的 Top-K 片段。 */
    @Transactional(readOnly = true)
    public List<SearchResultVO> search(Long projectId, Long operatorId, String query, Integer topK) {
        permissionService.requireMember(projectId, operatorId);
        if (query == null || query.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检索内容不能为空");
        }
        int limit = (topK == null || topK <= 0) ? 5 : Math.min(topK, 20);

        List<Double> vector = embeddingProvider.embed(query.trim());
        return qdrantClient.search(vector, limit, projectId).stream()
                .map(this::toSearchResult)
                .toList();
    }

    /** RAG 问答：检索项目上下文后交给大模型回答，并附带引用来源。 */
    @Transactional(readOnly = true)
    public AskAnswerVO ask(Long projectId, Long operatorId, String query, Integer topK) {
        List<SearchResultVO> contexts = search(projectId, operatorId, query, topK);
        if (contexts.isEmpty()) {
            return new AskAnswerVO("在项目资料中未找到相关依据，请先上传文档或代码并构建知识库。",
                    List.of(), chatProvider.modelName());
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            SearchResultVO ctx = contexts.get(i);
            contextBuilder.append("[").append(i + 1).append("] 来源：")
                    .append(ctx.getSourceType()).append("#").append(ctx.getSourceId())
                    .append("（相关度 ").append(String.format("%.2f", ctx.getScore())).append("）\n")
                    .append(ctx.getContent()).append("\n\n");
        }

        String userPrompt = "项目资料：\n\n" + contextBuilder + "问题：" + query;
        String answer;
        try {
            answer = chatProvider.chat(SYSTEM_PROMPT, userPrompt);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("RAG 问答失败 | projectId={}", projectId, ex);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务调用失败");
        }

        List<CitationVO> citations = contexts.stream()
                .map(ctx -> new CitationVO(ctx.getSourceType(), ctx.getSourceId(),
                        ctx.getChunkIndex(), ctx.getScore(), snippet(ctx.getContent())))
                .toList();

        return new AskAnswerVO(answer, citations, chatProvider.modelName());
    }

    /** 清空知识库：删除向量并重置索引标记。 */
    @Transactional
    public void clear(Long projectId, Long operatorId) {
        permissionService.requireWriter(projectId, operatorId);

        qdrantClient.deleteByProject(projectId);

        List<FileChunk> chunks = chunkRepository.findByProjectId(projectId);
        chunks.forEach(chunk -> chunk.setIndexed(false));
        chunkRepository.saveAll(chunks);

        log.info("知识库已清空 | projectId={} | resetChunks={}", projectId, chunks.size());
    }

    private Map<String, Object> buildPayload(Long projectId, FileChunk chunk) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("project_id", projectId);
        payload.put("source_type", chunk.getSourceType().name());
        payload.put("source_id", chunk.getSourceId());
        payload.put("chunk_index", chunk.getChunkIndex());
        payload.put("content", chunk.getContent());
        return payload;
    }

    private SearchResultVO toSearchResult(QdrantClient.SearchHit hit) {
        Object content = hit.payload().get("content");
        Object sourceType = hit.payload().get("source_type");
        Object sourceId = hit.payload().get("source_id");
        Object chunkIndex = hit.payload().get("chunk_index");

        return new SearchResultVO(
                hit.id(),
                content == null ? "" : content.toString(),
                hit.score(),
                sourceType == null ? "" : sourceType.toString(),
                sourceId == null ? null : Long.valueOf(sourceId.toString()),
                chunkIndex == null ? null : Integer.valueOf(chunkIndex.toString()));
    }

    private String snippet(String content) {
        if (content == null) {
            return "";
        }
        String clean = content.replaceAll("\\s+", " ").trim();
        return clean.length() <= 120 ? clean : clean.substring(0, 120) + "…";
    }
}
