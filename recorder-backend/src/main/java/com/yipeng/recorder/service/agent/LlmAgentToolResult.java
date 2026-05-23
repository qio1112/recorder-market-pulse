package com.yipeng.recorder.service.agent;

public class LlmAgentToolResult {

    private final String toolName;
    private final boolean success;
    private final String content;
    private final String errorMessage;

    private LlmAgentToolResult(String toolName, boolean success, String content, String errorMessage) {
        this.toolName = toolName;
        this.success = success;
        this.content = content == null ? "" : content;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static LlmAgentToolResult success(String toolName, String content) {
        return new LlmAgentToolResult(toolName, true, content, null);
    }

    public static LlmAgentToolResult error(String toolName, String errorMessage) {
        return new LlmAgentToolResult(toolName, false, "", errorMessage);
    }

    public String getToolName() {
        return toolName;
    }

    public boolean isSuccess() {
        return success;
    }

    public String renderForModel() {
        if (success) {
            return content;
        }
        return "TOOL_ERROR %s\n%s".formatted(toolName == null ? "unknown" : toolName, errorMessage);
    }
}
