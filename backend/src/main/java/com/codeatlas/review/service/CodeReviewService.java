package com.codeatlas.review.service;

import com.codeatlas.ai.entity.AiExecutionLog;
import com.codeatlas.ai.provider.ChatProvider;
import com.codeatlas.ai.repository.AiExecutionLogRepository;
import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.project.service.ProjectPermissionService;
import com.codeatlas.review.dto.ReviewResultVO;
import com.codeatlas.review.entity.ReviewResult;
import com.codeatlas.review.repository.ReviewResultRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 代码审查服务。
 *
 * <p>链路：代码 / Diff / Commit → Prompt → LLM → 解析 JSON → 结构化落库。
 * 关键点是**让模型输出结构化 JSON 并解析**，而不是返回一大段文本
 * （见 docs/development.md 第 9 节）。
 */
@Service
public class CodeReviewService {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewService.class);

    private static final String SYSTEM_PROMPT = """
            你是一位资深代码审查专家，负责审查软件项目代码。

            请从以下四个维度发现问题：
            1. CORRECTNESS 正确性：潜在 Bug、异常处理缺失、边界条件问题
            2. PERFORMANCE 性能：不必要的循环、高复杂度操作、数据库查询问题
            3. SECURITY 安全：SQL 注入、敏感信息泄露、权限控制问题
            4. MAINTAINABILITY 可维护性：代码重复、模块耦合、命名与结构问题

            严格要求：
            - 只输出 JSON 数组，禁止输出任何解释性文字或 Markdown 代码块标记
            - 只报告真实存在的问题，没有问题就返回空数组 []
            - 每项必须包含字段：severity、category、file、line、description、risk、suggestion
            - severity 取值：INFO / MINOR / MAJOR / CRITICAL
            - category 取值：CORRECTNESS / PERFORMANCE / SECURITY / MAINTAINABILITY
            - line 为整数，无法确定时填 null
            - 使用中文描述
            """;

    private final ReviewResultRepository reviewRepository;

    private final ProjectPermissionService permissionService;

    private final ChatProvider chatProvider;

    private final AiExecutionLogRepository executionLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CodeReviewService(ReviewResultRepository reviewRepository,
                             ProjectPermissionService permissionService,
                             ChatProvider chatProvider,
                             AiExecutionLogRepository executionLogRepository) {
        this.reviewRepository = reviewRepository;
        this.permissionService = permissionService;
        this.chatProvider = chatProvider;
        this.executionLogRepository = executionLogRepository;
    }

    /** 执行审查：调用模型并解析为结构化结果后落库。 */
    @Transactional
    public List<ReviewResultVO> review(Long projectId, Long userId,
                                       ReviewResult.SourceType sourceType,
                                       String sourceRef, String content) {
        permissionService.requireWriter(projectId, userId);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审查内容不能为空");
        }

        String userPrompt = buildPrompt(sourceType, sourceRef, content);

        long start = System.currentTimeMillis();
        String raw;
        try {
            raw = chatProvider.chat(SYSTEM_PROMPT, userPrompt);
        } catch (BusinessException ex) {
            recordExecution(projectId, userId, (int) (System.currentTimeMillis() - start), false,
                    ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("代码审查调用失败 | projectId={}", projectId, ex);
            recordExecution(projectId, userId, (int) (System.currentTimeMillis() - start), false,
                    ex.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务调用失败");
        }

        List<ReviewItem> items;
        try {
            items = parseItems(raw);
        } catch (Exception ex) {
            log.error("审查结果解析失败 | raw={}", raw, ex);
            recordExecution(projectId, userId, (int) (System.currentTimeMillis() - start), false,
                    "结果解析失败");
            throw new BusinessException(ErrorCode.REVIEW_PARSE_FAILED, "AI 返回内容无法解析为结构化结果");
        }

        recordExecution(projectId, userId, (int) (System.currentTimeMillis() - start), true, null);

        List<ReviewResultVO> result = new ArrayList<>();
        for (ReviewItem item : items) {
            ReviewResult entity = new ReviewResult();
            entity.setProjectId(projectId);
            entity.setUserId(userId);
            entity.setSourceType(sourceType == null ? null : sourceType.name());
            entity.setSourceRef(sourceRef);
            entity.setSeverity(normalizeEnum(item.severity(), "INFO"));
            entity.setCategory(normalizeEnum(item.category(), "MAINTAINABILITY"));
            entity.setFilePath(item.file() == null ? sourceRef : item.file());
            entity.setLine(item.line());
            entity.setDescription(item.description());
            entity.setRisk(item.risk());
            entity.setSuggestion(item.suggestion());
            entity.setModel(chatProvider.modelName());
            result.add(ReviewResultVO.from(reviewRepository.save(entity)));
        }

        log.info("代码审查完成 | projectId={} | issues={} | model={}",
                projectId, result.size(), chatProvider.modelName());
        return result;
    }

    /** 项目审查结果列表。 */
    @Transactional(readOnly = true)
    public List<ReviewResultVO> list(Long projectId, Long userId, String severity) {
        permissionService.requireMember(projectId, userId);
        List<ReviewResult> results = (severity == null || severity.isBlank())
                ? reviewRepository.findByProjectIdOrderByIdDesc(projectId)
                : reviewRepository.findByProjectIdAndSeverityOrderByIdDesc(projectId, severity);
        return results.stream().map(ReviewResultVO::from).toList();
    }

    /** 单条审查结果详情。 */
    @Transactional(readOnly = true)
    public ReviewResultVO getById(Long resultId, Long userId) {
        ReviewResult result = reviewRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        permissionService.requireMember(result.getProjectId(), userId);
        return ReviewResultVO.from(result);
    }

    private String buildPrompt(ReviewResult.SourceType sourceType, String sourceRef,
                               String content) {
        return "审查内容类型：" + (sourceType == null ? "SNIPPET" : sourceType.name()) + "\n"
                + "来源标识：" + (sourceRef == null ? "未提供" : sourceRef) + "\n\n"
                + "内容：\n" + content;
    }

    /**
     * 解析模型返回：容忍被 Markdown 代码块包裹或前后带解释文字的情况。
     */
    private List<ReviewItem> parseItems(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.trim();
        // 去掉 ```json ... ``` 包裹
        cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```$", "").trim();

        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("未找到 JSON 数组");
        }
        String json = cleaned.substring(start, end + 1);

        return objectMapper.readValue(json, new TypeReference<List<ReviewItem>>() {
        });
    }

    /** 模型可能返回小写或非法值，统一规范化。 */
    private String normalizeEnum(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase();
    }

    private void recordExecution(Long projectId, Long userId, int elapsedMs,
                                 boolean success, String error) {
        AiExecutionLog logEntity = new AiExecutionLog();
        logEntity.setProjectId(projectId);
        logEntity.setUserId(userId);
        logEntity.setRequestType("REVIEW");
        logEntity.setModel(chatProvider.modelName());
        logEntity.setExecutionTimeMs(elapsedMs);
        logEntity.setSuccess(success);
        logEntity.setErrorMessage(error);
        executionLogRepository.save(logEntity);
    }

    /** 模型返回的单条问题。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReviewItem(String severity, String category, String file, Integer line,
                             String description, String risk, String suggestion) {
    }
}
