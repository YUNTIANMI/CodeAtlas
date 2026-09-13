package com.codeatlas.review.service;

import com.codeatlas.ai.provider.ChatProvider;
import com.codeatlas.ai.repository.AiExecutionLogRepository;
import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.project.service.ProjectPermissionService;
import com.codeatlas.review.dto.ReviewResultVO;
import com.codeatlas.review.entity.ReviewResult;
import com.codeatlas.review.repository.ReviewResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 代码审查服务测试：重点验证模型输出的结构化解析。
 */
@ExtendWith(MockitoExtension.class)
class CodeReviewServiceTest {

    @Mock
    private ReviewResultRepository reviewRepository;

    @Mock
    private ProjectPermissionService permissionService;

    @Mock
    private ChatProvider chatProvider;

    @Mock
    private AiExecutionLogRepository executionLogRepository;

    @InjectMocks
    private CodeReviewService codeReviewService;

    private static final Long PROJECT_ID = 10L;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        when(chatProvider.modelName()).thenReturn("deepseek:deepseek-chat");
    }

    private static final String SAMPLE_CODE = """
            public class UserService {
                public User findByUsername(String username) {
                    String sql = "SELECT * FROM users WHERE username = '" + username + "'";
                    return jdbcTemplate.queryForObject(sql, User.class);
                }
            }
            """;

    private static final String JSON_RESPONSE = """
            [
              {
                "severity": "CRITICAL",
                "category": "SECURITY",
                "file": "UserService.java",
                "line": 3,
                "description": "使用字符串拼接构造 SQL，存在 SQL 注入风险",
                "risk": "攻击者可构造用户名绕过认证或拖库",
                "suggestion": "改用参数化查询 PreparedStatement"
              },
              {
                "severity": "MINOR",
                "category": "CORRECTNESS",
                "file": "UserService.java",
                "line": 4,
                "description": "未处理查询不到用户的空结果",
                "risk": "可能抛出空指针异常",
                "suggestion": "增加结果判断并返回业务异常"
              }
            ]
            """;

    @Test
    @DisplayName("审查成功：解析模型 JSON 并保存为结构化结果")
    void reviewSuccess() {
        when(chatProvider.chat(anyString(), anyString())).thenReturn(JSON_RESPONSE);
        when(reviewRepository.save(any(ReviewResult.class))).thenAnswer(invocation -> {
            ReviewResult r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });

        List<ReviewResultVO> results = codeReviewService.review(PROJECT_ID, USER_ID,
                ReviewResult.SourceType.FILE, "UserService.java", SAMPLE_CODE);

        assertEquals(2, results.size());
        assertEquals("CRITICAL", results.get(0).getSeverity());
        assertEquals("SECURITY", results.get(0).getCategory());
        assertEquals(Integer.valueOf(3), results.get(0).getLine());
        assertTrue(results.get(0).getDescription().contains("SQL 注入"));
        assertEquals("UserService.java", results.get(0).getFilePath());
        verify(reviewRepository, org.mockito.Mockito.times(2)).save(any(ReviewResult.class));
    }

    @Test
    @DisplayName("解析容错：模型用 Markdown 代码块包裹 JSON 也能解析")
    void reviewWithMarkdownWrapper() {
        String wrapped = "```json\n" + JSON_RESPONSE + "\n```";
        when(chatProvider.chat(anyString(), anyString())).thenReturn(wrapped);
        when(reviewRepository.save(any(ReviewResult.class))).thenAnswer(invocation -> {
            ReviewResult r = invocation.getArgument(0);
            r.setId(2L);
            return r;
        });

        List<ReviewResultVO> results = codeReviewService.review(PROJECT_ID, USER_ID,
                ReviewResult.SourceType.SNIPPET, null, SAMPLE_CODE);

        assertEquals(2, results.size());
        assertEquals("SECURITY", results.get(0).getCategory());
    }

    @Test
    @DisplayName("无问题代码：返回空列表")
    void reviewNoIssues() {
        when(chatProvider.chat(anyString(), anyString())).thenReturn("[]");

        List<ReviewResultVO> results = codeReviewService.review(PROJECT_ID, USER_ID,
                ReviewResult.SourceType.SNIPPET, null, "int a = 1;");

        assertTrue(results.isEmpty());
        verify(reviewRepository, never()).save(any(ReviewResult.class));
    }

    @Test
    @DisplayName("审查失败：内容为空")
    void reviewEmptyContent() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> codeReviewService.review(PROJECT_ID, USER_ID,
                        ReviewResult.SourceType.SNIPPET, null, "   "));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(chatProvider, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("审查失败：无写入权限（VIEWER 不能提交审查）")
    void reviewWithoutPermission() {
        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION))
                .when(permissionService).requireWriter(PROJECT_ID, 2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> codeReviewService.review(PROJECT_ID, 2L,
                        ReviewResult.SourceType.SNIPPET, null, SAMPLE_CODE));
        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, ex.getErrorCode());
        verify(chatProvider, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("解析失败：模型返回非 JSON 内容")
    void reviewParseFailure() {
        when(chatProvider.chat(anyString(), anyString()))
                .thenReturn("抱歉，我无法完成这次审查。");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> codeReviewService.review(PROJECT_ID, USER_ID,
                        ReviewResult.SourceType.SNIPPET, null, SAMPLE_CODE));
        assertEquals(ErrorCode.REVIEW_PARSE_FAILED, ex.getErrorCode());
        verify(reviewRepository, never()).save(any(ReviewResult.class));
    }

    @Test
    @DisplayName("AI 调用失败：记录失败日志并抛出")
    void reviewAiFailure() {
        when(chatProvider.chat(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR));

        assertThrows(BusinessException.class,
                () -> codeReviewService.review(PROJECT_ID, USER_ID,
                        ReviewResult.SourceType.SNIPPET, null, SAMPLE_CODE));
        verify(executionLogRepository).save(any(com.codeatlas.ai.entity.AiExecutionLog.class));
    }

    @Test
    @DisplayName("行号缺失：允许为 null")
    void reviewWithoutLine() {
        String json = """
                [{"severity":"INFO","category":"MAINTAINABILITY","file":"A.java",
                  "line":null,"description":"命名不规范","risk":"可读性差","suggestion":"重命名"}]
                """;
        when(chatProvider.chat(anyString(), anyString())).thenReturn(json);
        when(reviewRepository.save(any(ReviewResult.class))).thenAnswer(invocation -> {
            ReviewResult r = invocation.getArgument(0);
            r.setId(3L);
            return r;
        });

        List<ReviewResultVO> results = codeReviewService.review(PROJECT_ID, USER_ID,
                ReviewResult.SourceType.SNIPPET, null, SAMPLE_CODE);

        assertEquals(1, results.size());
        assertNull(results.get(0).getLine());
    }

    @Test
    @DisplayName("结果列表：可按严重等级过滤")
    void listBySeverity() {
        when(reviewRepository.findByProjectIdAndSeverityOrderByIdDesc(PROJECT_ID, "CRITICAL"))
                .thenReturn(List.of(buildResult("CRITICAL")));

        List<ReviewResultVO> results = codeReviewService.list(PROJECT_ID, USER_ID, "CRITICAL");

        assertEquals(1, results.size());
        assertEquals("CRITICAL", results.get(0).getSeverity());
        verify(permissionService).requireMember(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("结果详情：不存在时报错")
    void getByIdNotFound() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> codeReviewService.getById(999L, USER_ID));
        assertEquals(ErrorCode.REVIEW_NOT_FOUND, ex.getErrorCode());
    }

    private ReviewResult buildResult(String severity) {
        ReviewResult result = new ReviewResult();
        result.setId(1L);
        result.setProjectId(PROJECT_ID);
        result.setUserId(USER_ID);
        result.setSeverity(severity);
        result.setCategory("SECURITY");
        result.setFilePath("UserService.java");
        result.setLine(3);
        result.setDescription("SQL 注入风险");
        return result;
    }
}
