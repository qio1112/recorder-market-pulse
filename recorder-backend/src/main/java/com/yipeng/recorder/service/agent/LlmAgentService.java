package com.yipeng.recorder.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.prompt.BuiltInLlmTokenLimits;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class LlmAgentService {

    private static final Logger logger = LoggerFactory.getLogger(LlmAgentService.class);

    private static final String NO_RECORDS_FOUND_MESSAGE = "No related Recorder records were found for this question.";
    private static final String RECORD_SEARCH_FAILED_MESSAGE = "I could not search Recorder records for this question. Please try again or use related-context mode.";
    private static final String RECORDS_FOUND_BUT_NO_ANSWER_FALLBACK = "I found related Recorder records, but the local model did not produce a usable answer. Try rephrasing the question or use related-context mode.";
    private static final String NO_FINAL_ANSWER_FALLBACK = "I could not produce a useful final answer. Please try a more specific question.";
    private static final Duration AGENT_LLM_READ_TIMEOUT = Duration.ofSeconds(240);
    private static final Pattern TOOL_CALL_TOKEN_PATTERN = Pattern.compile("(?i)<\\|?/?tool_calls?\\|?>");
    private static final Pattern HAS_WORD_PATTERN = Pattern.compile("(?is).*\\b[a-z]{2,}\\b.*");
    private static final Pattern MULTI_CONCEPT_SPLIT_PATTERN = Pattern.compile("(?i)\\s+(and|or|vs|versus)\\s+|[,;/]+");

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
        LlmChatResponse response = marketPulseApiService.chatWithLlm(workingRequest, AGENT_LLM_READ_TIMEOUT);
        String reply = response == null ? "" : StringUtils.defaultString(response.getReply()).trim();
        AgentReply agentReply = parseAgentReply(reply);
        if (agentReply.finalAnswer() != null) {
            logger.info("LLM record agent completed without search: finalChars={}", agentReply.finalAnswer().length());
            return new LlmChatResponse(agentReply.finalAnswer());
        }
        if (agentReply.toolCall() == null) {
            logger.info("LLM record agent produced no usable tool request: finalChars={}, fallbackReason=initial_unusable_output", reply.length());
            return new LlmChatResponse(NO_FINAL_ANSWER_FALLBACK);
        }

        LlmAgentToolCall toolCall = agentReply.toolCall();
        if (!"search_records".equals(toolCall.getToolName())) {
            logger.info("LLM record agent requested unsupported tool: name={}, fallbackReason=unsupported_tool", toolCall.getToolName());
            return new LlmChatResponse(NO_FINAL_ANSWER_FALLBACK);
        }

        LlmAgentToolResult toolResult = executeTool(toolCall, user);
        List<String> toolUsages = buildToolUsages(toolCall);
        String toolContent = toolResult.renderForModel();
        boolean recordsFound = toolResult.isSuccess() && hasRelatedRecords(toolContent);
        int contextChars = toolContent.length();
        logger.info("LLM record agent search result: query={}, chunkCount={}, contextChars={}, success={}",
                getToolQuery(toolCall),
                estimateChunkCount(toolContent),
                contextChars,
                toolResult.isSuccess());
        if (!toolResult.isSuccess()) {
            logger.info("LLM record agent returning without final synthesis: query={}, fallbackReason=tool_error",
                    getToolQuery(toolCall));
            return new LlmChatResponse(RECORD_SEARCH_FAILED_MESSAGE, toolUsages);
        }
        if (!recordsFound) {
            logger.info("LLM record agent returning without final synthesis: query={}, fallbackReason=no_records",
                    getToolQuery(toolCall));
            return new LlmChatResponse(NO_RECORDS_FOUND_MESSAGE, toolUsages);
        }

        LlmChatRequest finalRequest = buildFinalAnswerRequest(request, toolContent);
        LlmChatResponse finalResponse = marketPulseApiService.chatWithLlm(finalRequest, AGENT_LLM_READ_TIMEOUT);
        String finalReply = finalResponse == null ? "" : StringUtils.defaultString(finalResponse.getReply()).trim();
        String finalAnswer = extractStrictFinalAnswer(finalReply);
        if (isUsefulFinalAnswer(finalAnswer) && !isLowSignalOutput(finalAnswer)) {
            logger.info("LLM record agent final synthesis completed: query={}, contextChars={}, finalChars={}",
                    getToolQuery(toolCall),
                    contextChars,
                    finalAnswer.length());
            return new LlmChatResponse(finalAnswer, toolUsages);
        }

        logger.info("LLM record agent final synthesis fallback: query={}, contextChars={}, finalChars={}, fallbackReason=unusable_final_output",
                getToolQuery(toolCall),
                contextChars,
                finalReply.length());
        return new LlmChatResponse(RECORDS_FOUND_BUT_NO_ANSWER_FALLBACK, toolUsages);
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
        copy.setMaxTokens(BuiltInLlmTokenLimits.RECORD_AGENT_CHAT_MAX_TOKENS);
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

    private LlmChatRequest buildFinalAnswerRequest(LlmChatRequest originalRequest, String toolContent) {
        LlmChatRequest finalRequest = new LlmChatRequest();
        finalRequest.setTemperature(originalRequest == null ? null : originalRequest.getTemperature());
        finalRequest.setMaxTokens(BuiltInLlmTokenLimits.RECORD_AGENT_CHAT_MAX_TOKENS);
        finalRequest.setChatMode("RECORD_AGENT");
        List<LlmChatMessage> messages = new ArrayList<>();
        messages.add(new LlmChatMessage("system", BuiltInPrompts.AGENT_FINAL_ANSWER_SYSTEM_PROMPT));
        messages.add(new LlmChatMessage("user", """
                User question:
                %s

                Recorder record excerpts:
                %s
                """.formatted(latestUserQuestion(originalRequest), toolContent).trim()));
        finalRequest.setMessages(messages);
        return finalRequest;
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
            return AgentReply.empty();
        }
        boolean hasToolCallToken = TOOL_CALL_TOKEN_PATTERN.matcher(raw).find();
        String cleaned = stripToolCallTokens(stripJsonFences(raw));
        List<String> candidates = extractJsonObjects(cleaned);
        if (candidates.isEmpty()) {
            return hasToolCallToken
                    ? AgentReply.empty()
                    : isUsefulFinalAnswer(reply)
                    ? AgentReply.finalAnswer(reply)
                    : AgentReply.empty();
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
                            : AgentReply.empty();
                }
                LlmAgentToolCall toolCall = parseToolCall(parsed);
                if (toolCall != null) {
                    return AgentReply.toolCall(toolCall);
                }
            } catch (JsonProcessingException | IllegalArgumentException ignored) {
                if (hasToolCallToken) {
                    return AgentReply.empty();
                }
            }
        }
        return parsedAnyJson
                ? AgentReply.empty()
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

    private String extractStrictFinalAnswer(String reply) {
        String raw = StringUtils.defaultString(reply).trim();
        if (raw.isBlank() || TOOL_CALL_TOKEN_PATTERN.matcher(raw).find()) {
            return "";
        }
        String cleaned = stripJsonFences(raw);
        if (isUsefulFinalAnswer(cleaned) && !looksLikeOnlyJsonObject(cleaned)) {
            return cleaned;
        }
        AgentReply parsed = parseAgentReply(cleaned);
        if (parsed.toolCall() != null) {
            return "";
        }
        return parsed.finalAnswer() == null ? "" : parsed.finalAnswer();
    }

    private boolean looksLikeOnlyJsonObject(String value) {
        String normalized = StringUtils.defaultString(value).trim();
        return normalized.startsWith("{") && normalized.endsWith("}");
    }

    private boolean hasRelatedRecords(String toolContent) {
        String normalized = StringUtils.defaultString(toolContent).toLowerCase(Locale.ROOT);
        return normalized.contains("related records found:")
                && !normalized.contains("no related records were found");
    }

    private int estimateChunkCount(String toolContent) {
        String normalized = StringUtils.defaultString(toolContent);
        int marker = normalized.indexOf("Related records found:");
        if (marker < 0) {
            return 0;
        }
        String suffix = normalized.substring(marker + "Related records found:".length()).trim();
        int end = 0;
        while (end < suffix.length() && Character.isDigit(suffix.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(suffix.substring(0, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String latestUserQuestion(LlmChatRequest request) {
        if (request == null || request.getMessages() == null) {
            return "";
        }
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            LlmChatMessage message = request.getMessages().get(i);
            if (message != null && "user".equalsIgnoreCase(StringUtils.defaultString(message.getRole()))) {
                return StringUtils.defaultString(message.getContent()).trim();
            }
        }
        return "";
    }

    private String getToolQuery(LlmAgentToolCall toolCall) {
        List<String> queries = getToolQueries(toolCall);
        if (!queries.isEmpty()) {
            return String.join("; ", queries);
        }
        return "";
    }

    private List<String> getToolQueries(LlmAgentToolCall toolCall) {
        if (toolCall == null || toolCall.getArguments() == null) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        Object queries = toolCall.getArguments().get("queries");
        if (queries instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (value instanceof String text && StringUtils.isNotBlank(text)) {
                    values.add(text.trim());
                }
            }
            if (!values.isEmpty()) {
                return new ArrayList<>(values);
            }
        }
        Object query = toolCall.getArguments().get("query");
        if (query instanceof String text && StringUtils.isNotBlank(text)) {
            String[] parts = MULTI_CONCEPT_SPLIT_PATTERN.split(text.trim());
            for (String part : parts) {
                if (StringUtils.isNotBlank(part)) {
                    values.add(part.trim());
                }
            }
        }
        return new ArrayList<>(values);
    }

    private List<String> buildToolUsages(LlmAgentToolCall toolCall) {
        if (toolCall == null || !"search_records".equals(toolCall.getToolName())) {
            return List.of();
        }
        List<String> queries = getToolQueries(toolCall);
        if (queries.isEmpty()) {
            return List.of("Used qdrant tool to search []");
        }
        String quotedQueries = queries.stream()
                .map(query -> "'" + query.replace("'", "\\'") + "'")
                .collect(java.util.stream.Collectors.joining(", "));
        return List.of("Used qdrant tool to search [" + quotedQueries + "]");
    }

    private boolean isLowSignalOutput(String value) {
        String normalized = StringUtils.defaultString(value).trim();
        if (normalized.length() < 40) {
            return false;
        }
        if (!HAS_WORD_PATTERN.matcher(normalized).matches()) {
            return true;
        }
        long whitespace = normalized.chars().filter(Character::isWhitespace).count();
        long lettersOrDigits = normalized.chars().filter(Character::isLetterOrDigit).count();
        double whitespaceRatio = whitespace / (double) normalized.length();
        double lettersOrDigitsRatio = lettersOrDigits / (double) normalized.length();
        return whitespaceRatio < 0.03d && lettersOrDigitsRatio > 0.80d;
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

    private record AgentReply(String finalAnswer, LlmAgentToolCall toolCall) {
        private static AgentReply finalAnswer(String finalAnswer) {
            return new AgentReply(finalAnswer, null);
        }

        private static AgentReply toolCall(LlmAgentToolCall toolCall) {
            return new AgentReply(null, toolCall);
        }

        private static AgentReply empty() {
            return new AgentReply(null, null);
        }
    }
}
