package com.yipeng.recorder.service.agent;

import java.util.Collections;
import java.util.Map;

public class LlmAgentToolCall {

    private final String toolName;
    private final Map<String, Object> arguments;

    public LlmAgentToolCall(String toolName, Map<String, Object> arguments) {
        this.toolName = toolName;
        this.arguments = arguments == null ? Collections.emptyMap() : arguments;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }
}
