package com.codeatlas.agent.dto;

import java.util.List;

/**
 * Agent 执行结果：最终回答 + 完整工具调用轨迹。
 */
public class AgentResponse {

    private String task;

    private String answer;

    private List<ToolCallVO> toolCalls = List.of();

    private String model;

    public AgentResponse() {
    }

    public AgentResponse(String task, String answer, List<ToolCallVO> toolCalls, String model) {
        this.task = task;
        this.answer = answer;
        this.toolCalls = toolCalls == null ? List.of() : toolCalls;
        this.model = model;
    }

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<ToolCallVO> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCallVO> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
