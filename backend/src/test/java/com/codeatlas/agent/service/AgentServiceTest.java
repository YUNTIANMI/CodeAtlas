package com.codeatlas.agent.service;

import com.codeatlas.agent.dto.AgentResponse;
import com.codeatlas.agent.dto.ToolCallVO;
import com.codeatlas.agent.entity.AgentToolCall;
import com.codeatlas.agent.repository.AgentToolCallRepository;
import com.codeatlas.agent.tool.AgentTool;
import com.codeatlas.ai.provider.ChatProvider;
import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 服务测试：工具选择、多轮执行、失败处理与调用记录。
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentTool searchCodeTool;

    @Mock
    private ProjectPermissionService permissionService;

    @Mock
    private ChatProvider chatProvider;

    @Mock
    private AgentToolCallRepository toolCallRepository;

    private AgentService agentService;

    private static final Long PROJECT_ID = 10L;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        lenient().when(searchCodeTool.name()).thenReturn("search_code");
        lenient().when(searchCodeTool.description()).thenReturn("按关键字搜索代码");
        lenient().when(searchCodeTool.parametersDescription()).thenReturn("keyword（必填）");
        lenient().when(chatProvider.modelName()).thenReturn("deepseek:deepseek-chat");

        agentService = new AgentService(List.of(searchCodeTool), permissionService,
                chatProvider, toolCallRepository);
    }

    private static final String TOOL_CALL_JSON =
            "{\"action\":\"tool\",\"tool\":\"search_code\",\"input\":{\"keyword\":\"login\"}}";

    private static final String ANSWER_JSON =
            "{\"action\":\"answer\",\"content\":\"登录功能位于 UserService.java\"}";

    @Test
    @DisplayName("无需工具时直接返回答案")
    void answerDirectly() {
        when(chatProvider.chat(anyString(), anyString())).thenReturn(ANSWER_JSON);

        AgentResponse response = agentService.run(PROJECT_ID, USER_ID, "登录在哪实现的");

        assertEquals("登录功能位于 UserService.java", response.getAnswer());
        assertTrue(response.getToolCalls().isEmpty());
        verify(searchCodeTool, never()).execute(anyLong(), any());
        verify(permissionService).requireMember(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("调用工具后用观察结果继续推理，最终给出答案")
    void callToolThenAnswer() {
        when(chatProvider.chat(anyString(), anyString()))
                .thenReturn(TOOL_CALL_JSON, ANSWER_JSON);
        when(searchCodeTool.execute(PROJECT_ID, Map.of("keyword", "login")))
                .thenReturn("找到 1 个文件：UserService.java");

        AgentResponse response = agentService.run(PROJECT_ID, USER_ID, "登录在哪实现的");

        assertEquals("登录功能位于 UserService.java", response.getAnswer());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("search_code", response.getToolCalls().get(0).getToolName());
        assertTrue(response.getToolCalls().get(0).getSuccess());
        assertNotNull(response.getToolCalls().get(0).getExecutionTimeMs());
        verify(toolCallRepository).save(any(AgentToolCall.class));
    }

    @Test
    @DisplayName("调用不存在的工具：记录失败并继续")
    void unknownTool() {
        String unknown = "{\"action\":\"tool\",\"tool\":\"delete_file\",\"input\":{}}";
        when(chatProvider.chat(anyString(), anyString())).thenReturn(unknown, ANSWER_JSON);

        AgentResponse response = agentService.run(PROJECT_ID, USER_ID, "删掉文件");

        assertEquals(1, response.getToolCalls().size());
        assertFalse(response.getToolCalls().get(0).getSuccess());
        assertEquals("登录功能位于 UserService.java", response.getAnswer());
    }

    @Test
    @DisplayName("工具执行异常：记录失败信息，不中断任务")
    void toolExecutionFailure() {
        when(chatProvider.chat(anyString(), anyString())).thenReturn(TOOL_CALL_JSON, ANSWER_JSON);
        when(searchCodeTool.execute(anyLong(), any()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        AgentResponse response = agentService.run(PROJECT_ID, USER_ID, "登录在哪实现的");

        assertEquals(1, response.getToolCalls().size());
        assertFalse(response.getToolCalls().get(0).getSuccess());
        assertEquals("数据库连接失败", response.getToolCalls().get(0).getErrorMessage());
        assertEquals("登录功能位于 UserService.java", response.getAnswer());
    }

    @Test
    @DisplayName("任务为空时拒绝执行")
    void emptyTask() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> agentService.run(PROJECT_ID, USER_ID, "   "));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(chatProvider, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("非项目成员无法执行 Agent")
    void withoutPermission() {
        doThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER))
                .when(permissionService).requireMember(PROJECT_ID, 999L);

        assertThrows(BusinessException.class,
                () -> agentService.run(PROJECT_ID, 999L, "登录在哪实现的"));
        verify(chatProvider, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("模型返回非 JSON 时，将其输出作为最终答案")
    void nonJsonResponse() {
        when(chatProvider.chat(anyString(), anyString()))
                .thenReturn("登录功能由 UserService 处理。");

        AgentResponse response = agentService.run(PROJECT_ID, USER_ID, "登录在哪实现的");

        assertEquals("登录功能由 UserService 处理。", response.getAnswer());
        assertTrue(response.getToolCalls().isEmpty());
    }

    @Test
    @DisplayName("模型持续调用工具时受最大步数保护")
    void maxStepsGuard() {
        when(chatProvider.chat(anyString(), anyString())).thenReturn(TOOL_CALL_JSON);
        when(searchCodeTool.execute(anyLong(), any())).thenReturn("一些结果");

        AgentResponse response = agentService.run(PROJECT_ID, USER_ID, "无限循环测试");

        // 最多 5 轮，不会无限执行
        assertTrue(response.getToolCalls().size() <= 5);
        assertTrue(response.getAnswer().contains("最大工具调用次数"));
        verify(chatProvider, times(5)).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("模型调用失败时抛出 AI 服务错误")
    void aiFailure() {
        when(chatProvider.chat(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> agentService.run(PROJECT_ID, USER_ID, "登录在哪实现的"));
        assertEquals(ErrorCode.AI_SERVICE_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("工具调用历史可查询")
    void history() {
        AgentToolCall call = new AgentToolCall();
        call.setToolName("search_code");
        call.setInputContent("keyword=login");
        call.setOutputContent("找到 1 个文件");
        call.setExecutionTimeMs(120);
        call.setSuccess(true);
        when(toolCallRepository.findTop50ByProjectIdOrderByIdDesc(PROJECT_ID))
                .thenReturn(List.of(call));

        List<ToolCallVO> history = agentService.history(PROJECT_ID, USER_ID);

        assertEquals(1, history.size());
        assertEquals("search_code", history.get(0).getToolName());
        assertEquals(120, history.get(0).getExecutionTimeMs());
        verify(permissionService).requireMember(PROJECT_ID, USER_ID);
    }
}
