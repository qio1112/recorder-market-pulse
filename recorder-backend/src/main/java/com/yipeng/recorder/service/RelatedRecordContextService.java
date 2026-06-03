package com.yipeng.recorder.service;

import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.prompt.BuiltInPrompts;
import com.yipeng.recorder.response.QdrantQueryResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class RelatedRecordContextService {

    private static final Logger logger = LoggerFactory.getLogger(RelatedRecordContextService.class);

    public static final int DEFAULT_CHUNK_LIMIT = 5;
    public static final int DEFAULT_CANDIDATE_LIMIT = 20;
    public static final int DEFAULT_CHUNK_MAX_CHARS = 1200;
    public static final int DEFAULT_CONTEXT_MAX_CHARS = 7000;
    public static final int AGENT_CHUNK_LIMIT = 10;
    public static final int AGENT_CHUNK_MAX_CHARS = 850;
    public static final int AGENT_CONTEXT_MAX_CHARS = 9000;
    public static final double DEFAULT_THRESHOLD = 0.45d;
    private static final double OLD_RECORD_HIGH_SCORE = 0.72d;
    private static final int RECENT_DAYS = 120;
    private static final Pattern HISTORICAL_QUERY_PATTERN = Pattern.compile(
            ".*\\b(older|old|historical|history|archive|archived|past|previous|before|from \\d{4}|in \\d{4}|\\d{4})\\b.*"
    );

    private final QdrantEmbeddingService qdrantEmbeddingService;
    private final RecordService recordService;

    public RelatedRecordContextService(QdrantEmbeddingService qdrantEmbeddingService,
                                       RecordService recordService) {
        this.qdrantEmbeddingService = qdrantEmbeddingService;
        this.recordService = recordService;
    }

    public List<RelatedChunkContext> getRelatedChunkContexts(String query, User user) {
        return getRelatedChunkContexts(query, user, DEFAULT_CHUNK_LIMIT);
    }

    public List<RelatedChunkContext> getRelatedChunkContexts(String query, User user, Integer requestedLimit) {
        return getRelatedChunkContexts(
                query,
                user,
                requestedLimit,
                DEFAULT_CHUNK_LIMIT,
                chunkLimit -> Math.max(DEFAULT_CANDIDATE_LIMIT, chunkLimit * 4),
                Comparator
                        .comparing(RelatedChunkContext::oldRecord)
                        .thenComparing((RelatedChunkContext chunk) -> chunk.score() == null ? 0.0d : chunk.score(), Comparator.reverseOrder())
                        .thenComparing(RelatedChunkContext::lastModifiedAtForSort, Comparator.reverseOrder())
        );
    }

    public List<RelatedChunkContext> getAgentRelatedChunkContexts(String query, User user, Integer requestedLimit) {
        return getRelatedChunkContexts(
                query,
                user,
                requestedLimit,
                AGENT_CHUNK_LIMIT,
                chunkLimit -> Math.max(chunkLimit, chunkLimit * 2),
                Comparator
                        .comparing(RelatedChunkContext::lastModifiedAtForSort, Comparator.reverseOrder())
                        .thenComparing((RelatedChunkContext chunk) -> chunk.score() == null ? 0.0d : chunk.score(), Comparator.reverseOrder())
        );
    }

    private List<RelatedChunkContext> getRelatedChunkContexts(String query,
                                                             User user,
                                                             Integer requestedLimit,
                                                             int maxChunkLimit,
                                                             java.util.function.IntUnaryOperator candidateLimitFunction,
                                                             Comparator<RelatedChunkContext> chunkComparator) {
        if (StringUtils.isBlank(query)) {
            return List.of();
        }
        int chunkLimit = requestedLimit == null ? maxChunkLimit : Math.max(1, Math.min(requestedLimit, maxChunkLimit));
        List<QdrantQueryResult> results;
        try {
            results = qdrantEmbeddingService.querySimilarRecords(
                    query,
                    user,
                    DEFAULT_THRESHOLD,
                    candidateLimitFunction.applyAsInt(chunkLimit)
            );
        } catch (Exception e) {
            logger.warn("Failed to retrieve related record chunks", e);
            return List.of();
        }

        boolean includeOlderRecords = isHistoricalRecordQuery(query);
        ZonedDateTime recentCutoff = ZonedDateTime.now().minusDays(RECENT_DAYS);
        List<RelatedChunkContext> chunks = new ArrayList<>();
        for (QdrantQueryResult result : results) {
            Record record = loadVisibleRecord(result, user);
            if (record == null || result.getChunks() == null) {
                continue;
            }
            boolean oldRecord = isOldRecord(record, recentCutoff);
            if (!includeOlderRecords && oldRecord && !isHighScore(result.getBestScore())) {
                continue;
            }
            for (String chunk : result.getChunks()) {
                if (StringUtils.isBlank(chunk)) {
                    continue;
                }
                chunks.add(new RelatedChunkContext(
                        record.getId(),
                        record.getTitle(),
                        record.getCreationTime(),
                        record.getLastModifiedTime(),
                        result.getBestScore(),
                        oldRecord,
                        chunk
                ));
            }
        }
        return chunks.stream()
                .sorted(chunkComparator)
                .limit(chunkLimit)
                .toList();
    }

    public String buildRelatedChunksPrompt(List<RelatedChunkContext> chunks) {
        StringBuilder sb = new StringBuilder(BuiltInPrompts.RELATED_RECORD_CONTEXT_HEADER);
        appendChunkSections(sb, chunks);
        return sb.toString().trim();
    }

    public String buildToolResultContent(String query, List<RelatedChunkContext> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("TOOL_RESULT search_records\n");
        sb.append("Query: ").append(query == null ? "" : query).append("\n");
        if (chunks == null || chunks.isEmpty()) {
            sb.append("No related records were found.");
            return sb.toString();
        }
        sb.append("Use these bounded excerpts as background. Cite sources with bracketed Recorder record ids, for example [48].\n");
        sb.append("Do not cite excerpt indexes. Bracketed citations must be real Recorder record ids from the Source labels.\n");
        appendChunkSections(sb, chunks);
        return sb.toString().trim();
    }

    public String buildCompactToolResultContent(String query, List<RelatedChunkContext> chunks) {
        return buildCompactToolResultContent(
                StringUtils.isBlank(query) ? List.of() : List.of(query),
                chunks
        );
    }

    public String buildCompactToolResultContent(List<String> queries, List<RelatedChunkContext> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("TOOL_RESULT search_records\n");
        if (queries == null || queries.isEmpty()) {
            sb.append("Queries searched: \n");
        } else if (queries.size() == 1) {
            sb.append("Query: ").append(queries.get(0)).append("\n");
        } else {
            sb.append("Queries searched: ").append(String.join("; ", queries)).append("\n");
        }
        if (chunks == null || chunks.isEmpty()) {
            sb.append("No related records were found.");
            return sb.toString();
        }
        sb.append("Related records found: ").append(chunks.size()).append("\n");
        appendCompactChunkSections(sb, chunks);
        return sb.toString().trim();
    }

    private void appendCompactChunkSections(StringBuilder sb, List<RelatedChunkContext> chunks) {
        if (chunks == null) {
            return;
        }
        for (int i = 0; i < chunks.size(); i++) {
            RelatedChunkContext chunk = chunks.get(i);
            String section = """

                    Source [%d]: %s%s | modified %s%s
                    %s
                    """.formatted(
                    chunk.recordId(),
                    buildRelatedChunkTitleLabel(chunk),
                    chunk.score() == null ? "" : " | score %.3f".formatted(chunk.score()),
                    formatContextDate(chunk.modifiedAt()),
                    chunk.oldRecord() ? " | possibly outdated" : "",
                    truncate(chunk.text(), AGENT_CHUNK_MAX_CHARS)
            );
            if (sb.length() + section.length() > AGENT_CONTEXT_MAX_CHARS) {
                break;
            }
            sb.append(section);
        }
    }

    private void appendChunkSections(StringBuilder sb, List<RelatedChunkContext> chunks) {
        if (chunks == null) {
            return;
        }
        for (int i = 0; i < chunks.size(); i++) {
            RelatedChunkContext chunk = chunks.get(i);
            String section = """

                    Source [%d]: %s%s
                    Created: %s
                    Modified: %s%s
                    Excerpt:
                    %s
                    """.formatted(
                    chunk.recordId(),
                    buildRelatedChunkTitleLabel(chunk),
                    chunk.score() == null ? "" : " | score %.3f".formatted(chunk.score()),
                    formatContextDate(chunk.createdAt()),
                    formatContextDate(chunk.modifiedAt()),
                    chunk.oldRecord() ? " | possibly outdated" : "",
                    truncate(chunk.text(), DEFAULT_CHUNK_MAX_CHARS)
            );
            if (sb.length() + section.length() > DEFAULT_CONTEXT_MAX_CHARS) {
                break;
            }
            sb.append(section);
        }
    }

    private boolean isHistoricalRecordQuery(String query) {
        String normalized = StringUtils.defaultString(query).toLowerCase(Locale.ROOT);
        return HISTORICAL_QUERY_PATTERN.matcher(normalized).matches();
    }

    private boolean isOldRecord(Record record, ZonedDateTime recentCutoff) {
        ZonedDateTime modified = record.getLastModifiedTime() != null ? record.getLastModifiedTime() : record.getCreationTime();
        return modified != null && modified.isBefore(recentCutoff);
    }

    private boolean isHighScore(Double score) {
        return score != null && score >= OLD_RECORD_HIGH_SCORE;
    }

    private Record loadVisibleRecord(QdrantQueryResult result, User user) {
        try {
            Long id = Long.parseLong(result.getRecordId());
            Record record = recordService.getRecordById(id);
            return record != null && userCanSeeRecord(user, record) ? record : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean userCanSeeRecord(User user, Record record) {
        return user != null && record != null && (user.isAdmin()
                || record.isPublic()
                || (record.getCreatedBy() != null && record.getCreatedBy().getId().equals(user.getId())));
    }

    private String buildRelatedChunkTitleLabel(RelatedChunkContext chunk) {
        return StringUtils.defaultIfBlank(chunk.title(), "Untitled Record");
    }

    private String formatContextDate(ZonedDateTime value) {
        return value == null ? "unknown" : value.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    public record RelatedChunkContext(Long recordId,
                                      String title,
                                      ZonedDateTime createdAt,
                                      ZonedDateTime modifiedAt,
                                      Double score,
                                      boolean oldRecord,
                                      String text) {
        private ZonedDateTime lastModifiedAtForSort() {
            return modifiedAt != null ? modifiedAt : createdAt;
        }
    }
}
