package com.yipeng.recorder.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.prompt.BuiltInPrompts;
import com.yipeng.recorder.request.LlmChatMessage;
import com.yipeng.recorder.request.LlmChatRequest;
import com.yipeng.recorder.response.LlmChatResponse;
import com.yipeng.recorder.service.MarketPulseApiService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class LlmAgentService {

    private static final Logger logger = LoggerFactory.getLogger(LlmAgentService.class);

    private static final int MAX_TOOL_ITERATIONS = 3;
    private static final String NO_FINAL_ANSWER_FALLBACK = "I could not produce a useful final answer. No matching Recorder records may have been found; please try a more specific question.";
    private static final Duration AGENT_LLM_READ_TIMEOUT = Duration.ofSeconds(90);
    private static final Pattern TOOL_CALL_TOKEN_PATTERN = Pattern.compile("(?i)<\\|?/?tool_calls?\\|?>");

    private final MarketPulseApiService marketPulseApiService;
    private final LlmAgentToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public LlmAgentService(MarketPulseApiService marketPulseApiService,
                           LlmAgentToolRegistry toolRegistry,
                           ObjectMapper objectMapper) {
        this.marketPulseApiService = marketPulseApiService;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    public LlmChatResponse chatWithTools(LlmChatRequest request, User user) {
        LlmChatRequest workingRequest = copyRequestWithAgentInstructions(request);
        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            LlmChatResponse response = marketPulseApiService.chatWithLlm(workingRequest, AGENT_LLM_READ_TIMEOUT);
            String reply = response == null ? "" : StringUtils.defaultString(response.getReply()).trim();
            AgentReply agentReply = parseAgentReply(reply);
            if (agentReply.finalAnswer() != null) {
                return new LlmChatResponse(agentReply.finalAnswer());
            }
            if (agentReply.retryInstruction() != null) {
                workingRequest.getMessages().add(new LlmChatMessage("assistant", sanitizeAssistantReply(reply)));
                workingRequest.getMessages().add(new LlmChatMessage("system", agentReply.retryInstruction()));
                continue;
            }
            if (agentReply.toolCall() == null) {
                return new LlmChatResponse(reply);
            }
            LlmAgentToolResult toolResult = executeTool(agentReply.toolCall(), user);
            workingRequest.getMessages().add(new LlmChatMessage("assistant", renderToolCallForHistory(agentReply.toolCall())));
            workingRequest.getMessages().add(new LlmChatMessage("system", toolResult.renderForModel()));
        }

        workingRequest.getMessages().add(new LlmChatMessage(
                "system",
                BuiltInPrompts.AGENT_TOOL_ITERATION_LIMIT
        ));
        LlmChatResponse finalResponse = marketPulseApiService.chatWithLlm(workingRequest, AGENT_LLM_READ_TIMEOUT);
        String finalReply = finalResponse == null ? "" : StringUtils.defaultString(finalResponse.getReply()).trim();
        AgentReply parsedFinal = parseAgentReply(finalReply);
        String finalAnswer = parsedFinal.finalAnswer() != null ? parsedFinal.finalAnswer() : finalReply;
        return new LlmChatResponse(isUsefulFinalAnswer(finalAnswer) ? finalAnswer : NO_FINAL_ANSWER_FALLBACK);
    }

    private LlmAgentToolResult executeTool(LlmAgentToolCall toolCall, User user) {
        logger.info("LLM agent requested tool: name={}, argumentKeys={}, user={}",
                toolCall.getToolName(),
                toolCall.getArguments().keySet(),
                user == null ? "unknown" : user.getUsername());
        Optional<LlmAgentTool> tool = toolRegistry.find(toolCall.getToolName());
        if (tool.isEmpty()) {
            logger.warn("LLM agent requested unknown tool: name={}", toolCall.getToolName());
            return LlmAgentToolResult.error(toolCall.getToolName(), "Unknown tool. Available tools: " + availableToolNames());
        }
        try {
            LlmAgentToolResult result = tool.get().execute(toolCall.getArguments(), user);
            logger.info("LLM agent tool completed: name={}, success={}", toolCall.getToolName(), result.isSuccess());
            return result;
        } catch (Exception e) {
            logger.warn("LLM agent tool failed: name={}", toolCall.getToolName(), e);
            return LlmAgentToolResult.error(toolCall.getToolName(), "Tool execution failed: " + e.getMessage());
        }
    }

    private LlmChatRequest copyRequestWithAgentInstructions(LlmChatRequest request) {
        LlmChatRequest copy = new LlmChatRequest();
        copy.setTemperature(request == null ? null : request.getTemperature());
        copy.setMaxTokens(request == null ? null : request.getMaxTokens());
        copy.setChatMode("RECORD_AGENT");
        List<LlmChatMessage> messages = new ArrayList<>();
        messages.add(new LlmChatMessage("system", buildAgentSystemPrompt()));
        if (request != null && request.getMessages() != null) {
            request.getMessages().stream()
                    .filter(message -> message != null)
                    .filter(message -> !isInternalToolMarkerMessage(message))
                    .forEach(message -> messages.add(new LlmChatMessage(message.getRole(), message.getContent())));
        }
        copy.setMessages(messages);
        return copy;
    }

    private boolean isInternalToolMarkerMessage(LlmChatMessage message) {
        return "assistant".equalsIgnoreCase(StringUtils.defaultString(message.getRole()))
                && TOOL_CALL_TOKEN_PATTERN.matcher(StringUtils.defaultString(message.getContent())).find();
    }

    private String buildAgentSystemPrompt() {
        List<String> toolDescriptions = toolRegistry.getTools().stream()
                .map(tool -> "- %s: %s%n  Arguments: %s".formatted(
                        tool.getName(),
                        tool.getDescription(),
                        tool.getArgumentSchema()
                ))
                .toList();
        return BuiltInPrompts.buildAgentSystemPrompt(toolDescriptions);
    }

    private AgentReply parseAgentReply(String reply) {
        String raw = StringUtils.defaultString(reply).trim();
        if (raw.isBlank()) {
            return AgentReply.retry(BuiltInPrompts.AGENT_EMPTY_OR_PLACEHOLDER_FINAL);
        }
        boolean hasToolCallToken = TOOL_CALL_TOKEN_PATTERN.matcher(raw).find();
        String cleaned = stripToolCallTokens(stripJsonFences(raw));
        List<String> candidates = extractJsonObjects(cleaned);
        if (candidates.isEmpty()) {
            return hasToolCallToken
                    ? AgentReply.retry(BuiltInPrompts.AGENT_TOOL_CALL_PARSE_FAILURE)
                    : isUsefulFinalAnswer(reply)
                    ? AgentReply.finalAnswer(reply)
                    : AgentReply.retry(BuiltInPrompts.AGENT_EMPTY_OR_PLACEHOLDER_FINAL);
        }
        boolean parsedAnyJson = false;
        for (String candidate : candidates) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(candidate, new TypeReference<>() {});
                parsedAnyJson = true;
                Object finalAnswer = parsed.get("final");
                if (finalAnswer instanceof String text) {
                    return isUsefulFinalAnswer(text)
                            ? AgentReply.finalAnswer(text)
                            : AgentReply.retry(BuiltInPrompts.AGENT_EMPTY_OR_PLACEHOLDER_FINAL);
                }
                LlmAgentToolCall toolCall = parseToolCall(parsed);
                if (toolCall != null) {
                    return AgentReply.toolCall(toolCall);
                }
            } catch (JsonProcessingException | IllegalArgumentException ignored) {
                if (hasToolCallToken) {
                    return AgentReply.retry(BuiltInPrompts.AGENT_TOOL_CALL_PARSE_FAILURE);
                }
            }
        }
        return parsedAnyJson
                ? AgentReply.retry(BuiltInPrompts.AGENT_EMPTY_OR_PLACEHOLDER_FINAL)
                : AgentReply.finalAnswer(reply);
    }

    private boolean isUsefulFinalAnswer(String value) {
        String normalized = StringUtils.defaultString(value).trim();
        if (normalized.isBlank()) {
            return false;
        }
        String lower = normalized.toLowerCase();
        return !lower.equals("answer text")
                && !lower.equals("your actual answer")
                && !lower.equals("{}")
                && !lower.equals("{\"final\":\"answer text\"}")
                && !lower.equals("{\"final\":\"your actual answer\"}");
    }

    private LlmAgentToolCall parseToolCall(Map<String, Object> parsed) {
        Object toolName = parsed.get("tool");
        if (!(toolName instanceof String)) {
            toolName = parsed.get("name");
        }
        if (toolName instanceof String name) {
            return new LlmAgentToolCall(name, parseArguments(parsed.get("arguments")));
        }

        Object toolCalls = parsed.get("tool_calls");
        if (toolCalls instanceof List<?> calls && !calls.isEmpty() && calls.get(0) instanceof Map<?, ?> firstCall) {
            Map<String, Object> call = objectMapper.convertValue(firstCall, new TypeReference<>() {});
            Object function = call.get("function");
            if (function instanceof Map<?, ?> functionMap) {
                Map<String, Object> functionCall = objectMapper.convertValue(functionMap, new TypeReference<>() {});
                Object functionName = functionCall.get("name");
                if (functionName instanceof String name) {
                    return new LlmAgentToolCall(name, parseArguments(functionCall.get("arguments")));
                }
            }
        }
        return null;
    }

    private Map<String, Object> parseArguments(Object rawArguments) {
        if (rawArguments instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, new TypeReference<>() {});
        }
        if (rawArguments instanceof String text && !text.isBlank()) {
            try {
                return objectMapper.readValue(text, new TypeReference<>() {});
            } catch (JsonProcessingException ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private List<String> extractJsonObjects(String value) {
        String text = StringUtils.defaultString(value).trim();
        if (text.isBlank()) {
            return List.of();
        }
        List<String> objects = new ArrayList<>();
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(text.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        if (objects.isEmpty() && text.startsWith("{")) {
            objects.add(text);
        }
        return objects;
    }

    private String stripToolCallTokens(String value) {
        return TOOL_CALL_TOKEN_PATTERN.matcher(StringUtils.defaultString(value)).replaceAll("").trim();
    }

    private String sanitizeAssistantReply(String reply) {
        String sanitized = stripToolCallTokens(stripJsonFences(StringUtils.defaultString(reply).trim()));
        return sanitized.isBlank() ? "{}" : sanitized;
    }

    private String renderToolCallForHistory(LlmAgentToolCall toolCall) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "tool", toolCall.getToolName(),
                    "arguments", toolCall.getArguments()
            ));
        } catch (JsonProcessingException ignored) {
            return "{\"tool\":\"%s\",\"arguments\":{}}".formatted(toolCall.getToolName());
        }
    }

    private String stripJsonFences(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        String stripped = value.replaceFirst("(?s)^```(?:json)?\\s*", "");
        return stripped.replaceFirst("(?s)\\s*```$", "").trim();
    }

    private String availableToolNames() {
        return toolRegistry.getTools().stream()
                .map(LlmAgentTool::getName)
                .toList()
                .toString();
    }

    private record AgentReply(String finalAnswer, LlmAgentToolCall toolCall, String retryInstruction) {
        private static AgentReply finalAnswer(String finalAnswer) {
            return new AgentReply(finalAnswer, null, null);
        }

        private static AgentReply toolCall(LlmAgentToolCall toolCall) {
            return new AgentReply(null, toolCall, null);
        }

        private static AgentReply retry(String retryInstruction) {
            return new AgentReply(null, null, retryInstruction);
        }
    }
}
