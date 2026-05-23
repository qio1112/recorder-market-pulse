package com.yipeng.recorder.service.agent;

import com.yipeng.recorder.model.User;
import com.yipeng.recorder.service.RelatedRecordContextService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SearchRecordsAgentTool implements LlmAgentTool {

    private final RelatedRecordContextService relatedRecordContextService;

    public SearchRecordsAgentTool(RelatedRecordContextService relatedRecordContextService) {
        this.relatedRecordContextService = relatedRecordContextService;
    }

    @Override
    public String getName() {
        return "search_records";
    }

    @Override
    public String getDescription() {
        return "Searches Recorder records semantically and returns bounded excerpts with source metadata. Use this only when personal Recorder record context is needed.";
    }

    @Override
    public String getArgumentSchema() {
        return """
                {
                  "query": "required string: the semantic record search query",
                  "limit": "optional integer from 1 to 10; defaults to 10"
                }
                """.trim();
    }

    @Override
    public LlmAgentToolResult execute(Map<String, Object> arguments, User user) {
        String query = getString(arguments, "query");
        if (StringUtils.isBlank(query)) {
            return LlmAgentToolResult.error(getName(), "Missing required argument: query");
        }
        int limit = Math.min(
                getInteger(arguments, "limit", RelatedRecordContextService.AGENT_CHUNK_LIMIT),
                RelatedRecordContextService.AGENT_CHUNK_LIMIT
        );
        List<RelatedRecordContextService.RelatedChunkContext> chunks =
                relatedRecordContextService.getAgentRelatedChunkContexts(query, user, limit);
        return LlmAgentToolResult.success(
                getName(),
                relatedRecordContextService.buildCompactToolResultContent(query, chunks)
        );
    }

    private String getString(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        return value instanceof String ? ((String) value).trim() : "";
    }

    private int getInteger(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
