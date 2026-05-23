package com.yipeng.recorder.service.agent;

import com.yipeng.recorder.model.User;

import java.util.Map;

public interface LlmAgentTool {

    String getName();

    String getDescription();

    String getArgumentSchema();

    LlmAgentToolResult execute(Map<String, Object> arguments, User user);
}
