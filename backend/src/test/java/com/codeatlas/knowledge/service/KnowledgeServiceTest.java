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
import com.codeatlas.knowledge.dto.IndexStatusVO;
import com.codeatlas.knowledge.dto.SearchResultVO;
import com.codeatlas.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库服务测试：构建、检索、RAG 问答与异常处理。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    private FileChunkRepository chunkRepository;

    @Mock
    private ProjectPermissionService permissionService;

    @Mock
    private EmbeddingProvider embeddingProvider;

    @Mock
    private ChatProvider chatProvider;

    @Mock
    private QdrantClient qdrantClient;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private AiProperties.Embedding embeddingProperties;

    @InjectMocks
    private KnowledgeService knowledgeService;

    private static final Long PROJECT_ID = 10L;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        // 部分用例（如无权限、空查询）不会走到配置读取，故使用 lenient 避免严格模式报错
        lenient().when(aiProperties.getEmbedding()).thenReturn(embeddingProperties);
        lenient().when(embeddingProperties.getBatchSize()).thenReturn(16);
    }

    private FileChunk chunk(Long id, String content) {
        FileChunk chunk = new FileChunk();
        chunk.setId(id);
        chunk.setProjectId(PROJECT_ID);
        chunk.setSourceType(FileChunk.SourceType.DOCUMENT);
        chunk.setSourceId(1L);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunk.setIndexed(false);
        return chunk;
    }

    @Test
    @DisplayName("构建知识库：向量化并写入 Qdrant，然后标记已索引")
    void buildIndex() {
        List<FileChunk> chunks = List.of(chunk(1L, "内容一"), chunk(2L, "内容二"));
        when(chunkRepository.findByProjectIdAndIndexedFalse(PROJECT_ID)).thenReturn(chunks);
        when(embeddingProvider.dimension()).thenReturn(1024);
        when(embeddingProvider.embedBatch(any()))
                .thenReturn(List.of(List.of(0.1, 0.2), List.of(0.3, 0.4)));

        BuildResultVO result = knowledgeService.buildIndex(PROJECT_ID, USER_ID);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getIndexed());
        assertEquals(0, result.getFailed());
        verify(qdrantClient).ensureCollection(1024);
        verify(qdrantClient).upsert(any());
        verify(chunkRepository).saveAll(chunks);
        assertTrue(chunks.stream().allMatch(FileChunk::getIndexed));
    }

    @Test
    @DisplayName("构建知识库：无待索引内容时直接返回 0")
    void buildIndexNothingToDo() {
        when(chunkRepository.findByProjectIdAndIndexedFalse(PROJECT_ID)).thenReturn(List.of());
        when(embeddingProvider.dimension()).thenReturn(1024);

        BuildResultVO result = knowledgeService.buildIndex(PROJECT_ID, USER_ID);

        assertEquals(0, result.getTotal());
        verify(embeddingProvider, never()).embedBatch(any());
    }

    @Test
    @DisplayName("构建知识库失败：无写入权限")
    void buildIndexWithoutPermission() {
        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION))
                .when(permissionService).requireWriter(PROJECT_ID, 2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeService.buildIndex(PROJECT_ID, 2L));
        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, ex.getErrorCode());
        verify(qdrantClient, never()).upsert(any());
    }

    @Test
    @DisplayName("构建知识库：Embedding 失败时计入 failed 而不中断")
    void buildIndexHandlesEmbeddingFailure() {
        List<FileChunk> chunks = List.of(chunk(1L, "内容一"));
        when(chunkRepository.findByProjectIdAndIndexedFalse(PROJECT_ID)).thenReturn(chunks);
        when(embeddingProvider.dimension()).thenReturn(1024);
        when(embeddingProvider.embedBatch(any()))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR));

        BuildResultVO result = knowledgeService.buildIndex(PROJECT_ID, USER_ID);

        assertEquals(1, result.getTotal());
        assertEquals(0, result.getIndexed());
        assertEquals(1, result.getFailed());
    }

    @Test
    @DisplayName("检索失败：查询内容为空")
    void searchWithBlankQuery() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeService.search(PROJECT_ID, USER_ID, "  ", 5));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("检索成功：返回相似度最高的片段")
    void search() {
        when(embeddingProvider.embed("登录流程")).thenReturn(List.of(0.5, 0.6));
        when(qdrantClient.search(any(), anyInt(), anyLong())).thenReturn(List.of(
                new QdrantClient.SearchHit(1L, 0.92, Map.of(
                        "content", "登录由 UserController 处理",
                        "source_type", "DOCUMENT",
                        "source_id", 1,
                        "chunk_index", 0))));

        List<SearchResultVO> results = knowledgeService.search(PROJECT_ID, USER_ID, "登录流程", 5);

        assertEquals(1, results.size());
        assertEquals("登录由 UserController 处理", results.get(0).getContent());
        assertEquals(0.92, results.get(0).getScore());
        assertEquals("DOCUMENT", results.get(0).getSourceType());
    }

    @Test
    @DisplayName("RAG 问答：无上下文时明确说明未找到依据，不调用模型")
    void askWithoutContext() {
        when(embeddingProvider.embed(anyString())).thenReturn(List.of(0.1));
        when(qdrantClient.search(any(), anyInt(), anyLong())).thenReturn(List.of());
        when(chatProvider.modelName()).thenReturn("deepseek:deepseek-chat");

        AskAnswerVO result = knowledgeService.ask(PROJECT_ID, USER_ID, "随便问个问题", 5);

        assertTrue(result.getAnswer().contains("未找到相关依据"));
        assertTrue(result.getCitations().isEmpty());
        verify(chatProvider, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("RAG 问答：基于检索到的上下文生成答案并附带引用")
    void askWithContext() {
        when(embeddingProvider.embed(anyString())).thenReturn(List.of(0.1));
        when(qdrantClient.search(any(), anyInt(), anyLong())).thenReturn(List.of(
                new QdrantClient.SearchHit(1L, 0.88, Map.of(
                        "content", "登录流程：UserController → UserService → UserRepository",
                        "source_type", "CODE",
                        "source_id", 7,
                        "chunk_index", 0))));
        when(chatProvider.chat(anyString(), anyString()))
                .thenReturn("登录流程由 UserController 接收请求，经 UserService 处理后查询 UserRepository。");
        when(chatProvider.modelName()).thenReturn("deepseek:deepseek-chat");

        AskAnswerVO result = knowledgeService.ask(PROJECT_ID, USER_ID, "登录流程是什么", 5);

        assertNotNull(result.getAnswer());
        assertTrue(result.getAnswer().contains("UserController"));
        assertEquals(1, result.getCitations().size());
        assertEquals("CODE", result.getCitations().get(0).getSourceType());
        assertEquals(7L, result.getCitations().get(0).getSourceId());
        verify(chatProvider).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("索引状态：统计总数与待索引数")
    void status() {
        when(chunkRepository.countByProjectId(PROJECT_ID)).thenReturn(10L);
        when(chunkRepository.findByProjectIdAndIndexedFalse(PROJECT_ID))
                .thenReturn(List.of(chunk(1L, "x"), chunk(2L, "y"), chunk(3L, "z"),
                        chunk(4L, "a")));
        when(qdrantClient.getCollection()).thenReturn("codeatlas");
        when(embeddingProvider.modelName()).thenReturn("ollama:bge-m3");

        IndexStatusVO status = knowledgeService.status(PROJECT_ID, USER_ID);

        assertEquals(10L, status.getTotalChunks());
        assertEquals(6L, status.getIndexedChunks());
        assertEquals(4L, status.getPendingChunks());
        assertEquals("codeatlas", status.getCollection());
        assertEquals("ollama:bge-m3", status.getEmbeddingModel());
    }

    @Test
    @DisplayName("清空知识库：删除向量并重置索引标记")
    void clear() {
        List<FileChunk> chunks = List.of(chunk(1L, "x"), chunk(2L, "y"));
        when(chunkRepository.findByProjectId(PROJECT_ID)).thenReturn(chunks);

        knowledgeService.clear(PROJECT_ID, USER_ID);

        verify(qdrantClient).deleteByProject(PROJECT_ID);
        verify(chunkRepository).saveAll(chunks);
        assertTrue(chunks.stream().noneMatch(FileChunk::getIndexed));
    }
}
