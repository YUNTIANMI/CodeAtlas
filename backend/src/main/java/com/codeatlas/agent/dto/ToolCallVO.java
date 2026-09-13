package com.codeatlas.agent.dto;

/**
 * 工具调用明细：记录工具名、入参、出参、耗时与成功与否。
 */
public class ToolCallVO {

    private String toolName;

    private String input;

    private String output;

    private Integer executionTimeMs;

    private Boolean success;

    private String errorMessage;

    public ToolCallVO() {
    }

    public ToolCallVO(String toolName, String input, String output,
                      Integer executionTimeMs, Boolean success, String errorMessage) {
        this.toolName = toolName;
        this.input = input;
        this.output = output;
        this.executionTimeMs = executionTimeMs;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public Integer getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Integer executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
