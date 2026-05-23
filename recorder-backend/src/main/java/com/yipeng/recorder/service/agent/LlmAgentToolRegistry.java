package com.yipeng.recorder.service.agent;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class LlmAgentToolRegistry {

    private final Map<String, LlmAgentTool> toolsByName;

    public LlmAgentToolRegistry(List<LlmAgentTool> tools) {
        Map<String, LlmAgentTool> registeredTools = new LinkedHashMap<>();
        tools.stream()
                .sorted(Comparator.comparing(LlmAgentTool::getName))
                .forEach(tool -> registeredTools.put(tool.getName(), tool));
        this.toolsByName = Map.copyOf(registeredTools);
    }

    public Optional<LlmAgentTool> find(String toolName) {
        return Optional.ofNullable(toolsByName.get(toolName));
    }

    public Collection<LlmAgentTool> getTools() {
        return toolsByName.values();
    }
}
