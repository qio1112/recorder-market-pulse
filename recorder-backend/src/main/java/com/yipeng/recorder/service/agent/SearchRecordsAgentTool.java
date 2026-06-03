package com.yipeng.recorder.service.agent;

import com.yipeng.recorder.model.User;
import com.yipeng.recorder.service.RelatedRecordContextService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SearchRecordsAgentTool implements LlmAgentTool {

    private static final int MAX_SEARCH_QUERIES = 5;
    private static final Pattern MULTI_CONCEPT_SPLIT_PATTERN = Pattern.compile("(?i)\\s+(and|or|vs|versus)\\s+|[,;/]+");

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
                  "query": "string: fallback semantic record search query",
                  "queries": "optional array of 1 to 5 concise search keywords or phrases for separate Qdrant searches; use this when the user asks about multiple stocks, companies, topics, decisions, or record concepts",
                  "limit": "optional integer from 1 to 10; defaults to 10"
                }
                """.trim();
    }

    @Override
    public LlmAgentToolResult execute(Map<String, Object> arguments, User user) {
        List<String> queries = getQueries(arguments);
        if (queries.isEmpty()) {
            return LlmAgentToolResult.error(getName(), "Missing required argument: query or queries");
        }
        int limit = Math.min(
                getInteger(arguments, "limit", RelatedRecordContextService.AGENT_CHUNK_LIMIT),
                RelatedRecordContextService.AGENT_CHUNK_LIMIT
        );
        List<RelatedRecordContextService.RelatedChunkContext> chunks = searchAndMergeChunks(queries, user, limit);
        return LlmAgentToolResult.success(
                getName(),
                relatedRecordContextService.buildCompactToolResultContent(queries, chunks)
        );
    }

    private List<RelatedRecordContextService.RelatedChunkContext> searchAndMergeChunks(List<String> queries, User user, int limit) {
        List<List<RelatedRecordContextService.RelatedChunkContext>> resultsByQuery = new ArrayList<>();
        for (String query : queries) {
            resultsByQuery.add(relatedRecordContextService.getAgentRelatedChunkContexts(query, user, limit));
        }

        Map<String, RelatedRecordContextService.RelatedChunkContext> merged = new LinkedHashMap<>();
        for (int index = 0; merged.size() < limit; index++) {
            boolean addedAtThisDepth = false;
            for (List<RelatedRecordContextService.RelatedChunkContext> chunks : resultsByQuery) {
                if (index >= chunks.size()) {
                    continue;
                }
                RelatedRecordContextService.RelatedChunkContext chunk = chunks.get(index);
                String key = chunk.recordId() + "\n" + StringUtils.defaultString(chunk.text());
                if (!merged.containsKey(key)) {
                    merged.put(key, chunk);
                    addedAtThisDepth = true;
                    if (merged.size() >= limit) {
                        break;
                    }
                }
            }
            if (!addedAtThisDepth) {
                break;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<String> getQueries(Map<String, Object> arguments) {
        Set<String> queries = new LinkedHashSet<>();
        Object values = arguments == null ? null : arguments.get("queries");
        if (values instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                addQuery(queries, value);
            }
        } else {
            addQuery(queries, values);
        }
        if (queries.isEmpty()) {
            addQueryOrSplit(queries, arguments == null ? null : arguments.get("query"));
        }
        return queries.stream().limit(MAX_SEARCH_QUERIES).toList();
    }

    private void addQueryOrSplit(Set<String> queries, Object value) {
        if (!(value instanceof String text)) {
            return;
        }
        String query = text.trim();
        if (StringUtils.isBlank(query)) {
            return;
        }
        String[] parts = MULTI_CONCEPT_SPLIT_PATTERN.split(query);
        if (parts.length <= 1) {
            addQuery(queries, query);
            return;
        }
        for (String part : parts) {
            addQuery(queries, part);
        }
    }

    private void addQuery(Set<String> queries, Object value) {
        if (queries.size() >= MAX_SEARCH_QUERIES || !(value instanceof String text)) {
            return;
        }
        String query = text.trim();
        if (StringUtils.isNotBlank(query)) {
            queries.add(query);
        }
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
