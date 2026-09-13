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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * AI Agent 服务：让模型自主选择并调用只读工具完成任务。
 *
 * <p>流程（docs/development.md 第 11 节）：
 * 任务 → 模型判断 → 调用工具 → 观察结果 → 继续分析 → 最终回答
 *
 * <p>安全约束（ADR-005）：工具全部只读、强制项目隔离、完整记录调用过程。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    /** 最大工具调用轮次，防止模型陷入死循环。 */
    private static final int MAX_STEPS = 5;

    private final Map<String, AgentTool> tools = new HashMap<>();

    private final ProjectPermissionService permissionService;

    private final ChatProvider chatProvider;

    private final AgentToolCallRepository toolCallRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentService(List<AgentTool> toolList,
                        ProjectPermissionService permissionService,
                        ChatProvider chatProvider,
                        AgentToolCallRepository toolCallRepository) {
        for (AgentTool tool : toolList) {
            tools.put(tool.name(), tool);
        }
        this.permissionService = permissionService;
        this.chatProvider = chatProvider;
        this.toolCallRepository = toolCallRepository;
    }

    /** 执行 Agent 任务。 */
    @Transactional
    public AgentResponse run(Long projectId, Long userId, String task) {
        permissionService.requireMember(projectId, userId);
        if (task == null || task.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务描述不能为空");
        }

        List<ToolCallVO> calls = new ArrayList<>();
        StringBuilder observations = new StringBuilder();
        String answer = null;

        for (int step = 1; step <= MAX_STEPS; step++) {
            String prompt = buildPrompt(task.trim(), observations.toString());

            String raw;
            try {
                raw = chatProvider.chat(buildSystemPrompt(), prompt);
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ex) {
                log.error("Agent 调用模型失败 | projectId={} | step={}", projectId, step, ex);
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 服务调用失败");
            }

            Decision decision = parseDecision(raw);
            if (decision == null) {
                // 模型未按要求返回 JSON，直接将其输出作为最终回答
                answer = raw;
                break;
            }
            if (decision.isFinalAnswer()) {
                answer = decision.content();
                break;
            }

            String toolName = decision.toolName();
            AgentTool tool = tools.get(toolName);
            Map<String, String> input = decision.input();

            long start = System.currentTimeMillis();
            String output;
            boolean success = true;
            String error = null;
            if (tool == null) {
                success = false;
                error = "未找到工具：" + toolName;
                output = error + "，可用工具：" + String.join(", ", tools.keySet());
            } else {
                try {
                    output = tool.execute(projectId, input);
                } catch (Exception ex) {
                    success = false;
                    error = ex.getMessage();
                    output = "工具执行失败：" + error;
                    log.warn("Agent 工具执行失败 | tool={} | projectId={}", toolName, projectId, ex);
                }
            }
            int elapsed = (int) (System.currentTimeMillis() - start);

            record(projectId, userId, task, toolName, input, output, elapsed, success, error, step);

            calls.add(new ToolCallVO(toolName, summarizeInput(input), truncate(output, 1000),
                    elapsed, success, error));

            observations.append("第 ").append(step).append(" 步，调用 ").append(toolName)
                    .append("，结果：\n").append(truncate(output, 2000)).append("\n\n");
        }

        if (answer == null) {
            answer = "已达到最大工具调用次数（" + MAX_STEPS + " 次）。以下是已收集到的信息，供你参考：\n\n"
                    + observations;
        }

        log.info("Agent 任务完成 | projectId={} | steps={} | model={}",
                projectId, calls.size(), chatProvider.modelName());
        return new AgentResponse(task, answer, calls, chatProvider.modelName());
    }

    /** 项目最近的工具调用记录，便于审计。 */
    @Transactional(readOnly = true)
    public List<ToolCallVO> history(Long projectId, Long userId) {
        permissionService.requireMember(projectId, userId);
        return toolCallRepository.findTop50ByProjectIdOrderByIdDesc(projectId)
                .stream()
                .map(call -> new ToolCallVO(call.getToolName(), call.getInputContent(),
                        truncate(call.getOutputContent(), 500), call.getExecutionTimeMs(),
                        call.getSuccess(), call.getErrorMessage()))
                .toList();
    }

    private String buildSystemPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                你是一个软件项目分析助手，可以调用工具获取项目信息后回答用户的问题。

                严格要求：
                - 每次只输出一个 JSON 对象，不要输出任何其他文字或 Markdown 标记
                - 需要更多信息时输出：{"action":"tool","tool":"工具名","input":{"参数名":"值"}}
                - 信息足够时输出：{"action":"answer","content":"你的最终回答"}
                - 只使用下列工具，不要编造不存在的工具
                - 所有工具都是只读的，你无法修改任何内容
                - 用中文回答，并说明结论来自哪些文件或提交

                可用工具：
                """);
        for (AgentTool tool : tools.values()) {
            builder.append("- ").append(tool.name()).append("：").append(tool.description())
                    .append("\n  参数：").append(tool.parametersDescription()).append("\n");
        }
        return builder.toString();
    }

    private String buildPrompt(String task, String observations) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户任务：").append(task).append("\n\n");
        if (observations != null && !observations.isBlank()) {
            builder.append("已执行的调查过程：\n").append(observations);
        }
        builder.append("请判断下一步：继续调用工具，还是直接给出最终回答。");
        return builder.toString();
    }

    /** 解析模型决策，容忍 Markdown 包裹；无法解析时返回 null。 */
    private Decision parseDecision(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim()
                .replaceAll("^```[a-zA-Z]*\\s*", "")
                .replaceAll("```$", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(cleaned.substring(start, end + 1));
            String action = node.path("action").asText("");
            if ("answer".equalsIgnoreCase(action)) {
                return Decision.answer(node.path("content").asText(""));
            }
            if ("tool".equalsIgnoreCase(action)) {
                String toolName = node.path("tool").asText("");
                Map<String, String> input = new HashMap<>();
                JsonNode inputNode = node.path("input");
                if (inputNode.isObject()) {
                    Iterator<String> fields = inputNode.fieldNames();
                    while (fields.hasNext()) {
                        String field = fields.next();
                        input.put(field, inputNode.path(field).asText(""));
                    }
                }
                return Decision.tool(toolName, input);
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }

    private void record(Long projectId, Long userId, String task, String toolName,
                        Map<String, String> input, String output, int elapsed,
                        boolean success, String error, int step) {
        AgentToolCall call = new AgentToolCall();
        call.setProjectId(projectId);
        call.setUserId(userId);
        call.setTask(task);
        call.setToolName(toolName);
        call.setInputContent(summarizeInput(input));
        call.setOutputContent(output);
        call.setExecutionTimeMs(elapsed);
        call.setSuccess(success);
        call.setErrorMessage(error);
        call.setStepIndex(step);
        toolCallRepository.save(call);
    }

    private String summarizeInput(Map<String, String> input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        input.forEach((key, value) -> builder.append(key).append("=").append(value).append("; "));
        return builder.toString().trim();
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    /** 模型的一步决策。 */
    private record Decision(String toolName, Map<String, String> input, String content) {

        boolean isFinalAnswer() {
            return toolName == null;
        }

        static Decision answer(String content) {
            return new Decision(null, Map.of(), content);
        }

        static Decision tool(String toolName, Map<String, String> input) {
            return new Decision(toolName, input, null);
        }
    }
}
