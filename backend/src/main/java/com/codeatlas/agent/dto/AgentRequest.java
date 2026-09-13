package com.codeatlas.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Agent 任务请求。
 */
public class AgentRequest {

    @NotBlank(message = "任务描述不能为空")
    private String task;

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }
}
