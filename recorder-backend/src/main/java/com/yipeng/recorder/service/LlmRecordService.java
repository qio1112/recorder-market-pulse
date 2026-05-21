package com.yipeng.recorder.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.request.LlmChatMessage;
import com.yipeng.recorder.request.LlmChatRequest;
import com.yipeng.recorder.response.LlmChatResponse;
import com.yipeng.recorder.response.QdrantQueryResult;
import com.yipeng.recorder.utils.DateTimeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LlmRecordService {

    private static final Logger logger = LoggerFactory.getLogger(LlmRecordService.class);

    private static final int DEFAULT_MAX_LABELS = 8;
    private static final int CHAT_RECORD_MAX_LABELS = 5;
    private static final int RELATED_CHAT_CHUNK_LIMIT = 5;
    private static final int RELATED_CHAT_CANDIDATE_LIMIT = 20;
    private static final int RELATED_CHAT_CHUNK_MAX_CHARS = 1200;
    private static final int RELATED_CHAT_CONTEXT_MAX_CHARS = 7000;
    private static final double RELATED_CHAT_THRESHOLD = 0.45d;
    private static final double RELATED_CHAT_OLD_RECORD_HIGH_SCORE = 0.72d;
    private static final int RELATED_CHAT_RECENT_DAYS = 365;
    private static final Pattern THINK_BLOCK_PATTERN = Pattern.compile("(?is)<think>.*?</think>");
    private static final Pattern JSON_STRING_PATTERN = Pattern.compile("\"([^\"]{1,80})\"");
    private static final Pattern LABEL_TOKEN_PATTERN = Pattern.compile("\\b[A-Za-z][A-Za-z0-9_-]{1,29}\\b");
    private static final List<String> GENERIC_LABELS = List.of(
            "RECORD", "NOTE", "CONTENT", "SUMMARY", "USER", "CHAT", "DISCUSSION",
            "THE", "AND", "FOR", "WITH", "FROM", "THIS", "THAT", "ABOUT", "INTO",
            "ARE", "WAS", "WERE", "HAS", "HAVE", "WILL", "CAN", "COULD", "SHOULD",
            "WOULD", "WHAT", "WHEN", "WHERE", "WHICH", "THEIR", "THERE", "THESE",
            "THOSE", "USING", "USED", "CREATE", "GENERATE", "OUTPUT", "TITLE",
            "LABEL", "LABELS"
    );

    private final MarketPulseApiService marketPulseApiService;
    private final RecordService recordService;
    private final QdrantEmbeddingService qdrantEmbeddingService;
    private final QdrantJobService qdrantJobService;
    private final DateTimeUtils dateTimeUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmRecordService(MarketPulseApiService marketPulseApiService,
                            RecordService recordService,
                            QdrantEmbeddingService qdrantEmbeddingService,
                            QdrantJobService qdrantJobService,
                            DateTimeUtils dateTimeUtils) {
        this.marketPulseApiService = marketPulseApiService;
        this.recordService = recordService;
        this.qdrantEmbeddingService = qdrantEmbeddingService;
        this.qdrantJobService = qdrantJobService;
        this.dateTimeUtils = dateTimeUtils;
    }

    public List<String> generateLabels(String title, String content, Integer maxLabels) {
        int limit = maxLabels == null ? DEFAULT_MAX_LABELS : Math.max(1, Math.min(maxLabels, 12));
        return generateLabels(title, content, limit, false);
    }

    private List<String> generateLabels(String title, String content, int limit, boolean preferSingleWords) {
        String text = """
                Title:
                %s

                Content:
                %s
                """.formatted(nullToBlank(title), truncate(nullToBlank(content), 6000));
        LlmChatRequest request = new LlmChatRequest();
        request.setTemperature(0.1);
        request.setMaxTokens(700);
        request.setMessages(List.of(
                new LlmChatMessage("system", """
                        Create short record labels from the provided title and content.
                        Do not think step by step. Do not include reasoning, explanation, markdown, or prose.
                        Return only a JSON array of strings.
                        Labels must be key information words or compact phrases, uppercase, no spaces, no punctuation except underscore, max 30 characters.
                        Prefer single-word labels when possible. Use two or more word combinations only when a single word loses important meaning.
                        Do not include generic words like RECORD, NOTE, CONTENT, SUMMARY, USER, CHAT, DISCUSSION.
                        """),
                new LlmChatMessage("user", "Return up to %d labels for this record.\n\n%s".formatted(limit, text))
        ));
        LlmChatResponse response = marketPulseApiService.chatWithLlm(request);
        List<String> labels = normalizeLabels(parseLabels(response.getReply(), limit), limit);
        if (labels.isEmpty()) {
            labels = deriveLabelsFromText("%s\n%s".formatted(nullToBlank(title), nullToBlank(content)), limit);
        }
        return labels;
    }

    public Record createChatRecord(List<LlmChatMessage> messages, boolean isPublic, User user) {
        String transcript = buildTranscript(messages);
        String summary = summarizeChat(transcript);
        String title = generateChatTitle(summary, transcript);
        List<String> labels = generateLabels(title, summary + "\n\n" + transcript, CHAT_RECORD_MAX_LABELS, true);
        String content = """
                Summary
                %s

                Transcript
                %s
                """.formatted(summary, transcript).trim();
        Record record = new Record(title, user, content, isPublic);
        Record savedRecord = recordService.createRecord(
                record,
                Collections.emptyList(),
                Collections.emptyList(),
                labels,
                user,
                null,
                isPublic,
                Map.of("source", "llm_chat")
        );
        qdrantJobService.queueUpsert(savedRecord);
        return savedRecord;
    }

    public LlmChatRequest enrichChatWithRelatedChunks(LlmChatRequest request, User user) {
        String latestUserMessage = getLatestUserMessage(request == null ? null : request.getMessages());
        if (latestUserMessage.isBlank()) {
            return request;
        }
        List<RelatedChunkContext> chunks = getRelatedChunkContexts(latestUserMessage, user, isHistoricalRecordQuery(latestUserMessage));
        if (chunks.isEmpty()) {
            return request;
        }

        LlmChatRequest enriched = new LlmChatRequest();
        enriched.setTemperature(request.getTemperature());
        enriched.setMaxTokens(request.getMaxTokens());
        enriched.setIncludeRelatedRecords(false);
        enriched.setMessages(insertRelatedContextMessage(request.getMessages(), buildRelatedChunksPrompt(chunks)));
        return enriched;
    }

    private String getLatestUserMessage(List<LlmChatMessage> messages) {
        if (messages == null) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            LlmChatMessage message = messages.get(i);
            if (message != null
                    && "user".equalsIgnoreCase(nullToBlank(message.getRole()))
                    && !nullToBlank(message.getContent()).isBlank()) {
                return message.getContent().trim();
            }
        }
        return "";
    }

    private boolean isHistoricalRecordQuery(String query) {
        String normalized = nullToBlank(query).toLowerCase(Locale.ROOT);
        return normalized.matches(".*\\b(older|old|historical|history|archive|archived|past|previous|before|from \\d{4}|in \\d{4}|\\d{4})\\b.*");
    }

    private List<RelatedChunkContext> getRelatedChunkContexts(String query, User user, boolean includeOlderRecords) {
        List<QdrantQueryResult> results;
        try {
            results = qdrantEmbeddingService.querySimilarRecords(query, user, RELATED_CHAT_THRESHOLD, RELATED_CHAT_CANDIDATE_LIMIT);
        } catch (Exception e) {
            logger.warn("Failed to retrieve related record chunks for LLM chat", e);
            return List.of();
        }

        ZonedDateTime recentCutoff = ZonedDateTime.now().minusDays(RELATED_CHAT_RECENT_DAYS);
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
                if (chunk == null || chunk.isBlank()) {
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
                .sorted(Comparator
                        .comparing(RelatedChunkContext::oldRecord)
                        .thenComparing((RelatedChunkContext chunk) -> chunk.score() == null ? 0.0d : chunk.score(), Comparator.reverseOrder())
                        .thenComparing(RelatedChunkContext::lastModifiedAtForSort, Comparator.reverseOrder()))
                .limit(RELATED_CHAT_CHUNK_LIMIT)
                .toList();
    }

    private boolean isOldRecord(Record record, ZonedDateTime recentCutoff) {
        ZonedDateTime modified = record.getLastModifiedTime() != null ? record.getLastModifiedTime() : record.getCreationTime();
        return modified != null && modified.isBefore(recentCutoff);
    }

    private boolean isHighScore(Double score) {
        return score != null && score >= RELATED_CHAT_OLD_RECORD_HIGH_SCORE;
    }

    private Record loadVisibleRecord(QdrantQueryResult result, User user) {
        try {
            Long id = Long.parseLong(result.getRecordId());
            Record record = recordService.getRecordById(id);
            return record != null && userServiceCanSeeRecord(user, record) ? record : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean userServiceCanSeeRecord(User user, Record record) {
        return user != null && record != null && (user.isAdmin()
                || record.isPublic()
                || (record.getCreatedBy() != null && record.getCreatedBy().getId().equals(user.getId())));
    }

    private List<LlmChatMessage> insertRelatedContextMessage(List<LlmChatMessage> messages, String contextPrompt) {
        List<LlmChatMessage> enrichedMessages = new ArrayList<>();
        boolean inserted = false;
        if (messages != null) {
            for (LlmChatMessage message : messages) {
                if (!inserted && message != null && !"system".equalsIgnoreCase(nullToBlank(message.getRole()))) {
                    enrichedMessages.add(new LlmChatMessage("system", contextPrompt));
                    inserted = true;
                }
                if (message != null) {
                    enrichedMessages.add(new LlmChatMessage(message.getRole(), message.getContent()));
                }
            }
        }
        if (!inserted) {
            enrichedMessages.add(new LlmChatMessage("system", contextPrompt));
        }
        return enrichedMessages;
    }

    private String buildRelatedChunksPrompt(List<RelatedChunkContext> chunks) {
        StringBuilder sb = new StringBuilder("""
                The following are relevant excerpts from existing Recorder records. Use them as background information for the user's question when helpful.
                Do not simply repeat or dump these excerpts. Synthesize an answer for the user.
                If the excerpts are not relevant, ignore them.
                Prefer newer records when multiple excerpts conflict. If a cited source is old, treat it as possibly outdated unless the user asked for historical information.
                When citing sources, do not cite bare record IDs. Cite the exact Source label, including both title and id, for example: "Project Notes (Record 48)".

                Relevant excerpts:
                """);
        for (int i = 0; i < chunks.size(); i++) {
            RelatedChunkContext chunk = chunks.get(i);
            String sourceLabel = buildRelatedChunkSourceLabel(chunk);
            String section = """

                    [%d] Source: %s%s
                    Created: %s
                    Modified: %s%s
                    Excerpt:
                    %s
                    """.formatted(
                    i + 1,
                    sourceLabel,
                    chunk.score() == null ? "" : " | score %.3f".formatted(chunk.score()),
                    formatContextDate(chunk.createdAt()),
                    formatContextDate(chunk.modifiedAt()),
                    chunk.oldRecord() ? " | possibly outdated" : "",
                    truncate(chunk.text(), RELATED_CHAT_CHUNK_MAX_CHARS)
            );
            if (sb.length() + section.length() > RELATED_CHAT_CONTEXT_MAX_CHARS) {
                break;
            }
            sb.append(section);
        }
        return sb.toString().trim();
    }

    private String buildRelatedChunkSourceLabel(RelatedChunkContext chunk) {
        String title = StringUtils.defaultIfBlank(chunk.title(), "Untitled Record");
        return "%s (Record %d)".formatted(title, chunk.recordId());
    }

    private String formatContextDate(ZonedDateTime value) {
        return value == null ? "unknown" : value.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private record RelatedChunkContext(Long recordId, String title, ZonedDateTime createdAt, ZonedDateTime modifiedAt, Double score, boolean oldRecord, String text) {
        private ZonedDateTime lastModifiedAtForSort() {
            return modifiedAt != null ? modifiedAt : createdAt;
        }
    }

    @Async
    public CompletableFuture<Void> createChatRecordAsync(List<LlmChatMessage> messages, boolean isPublic, User user) {
        List<LlmChatMessage> messagesSnapshot = copyMessages(messages);
        try {
            Record record = createChatRecord(messagesSnapshot, isPublic, user);
            logger.info("Created LLM chat record asynchronously: id={}, title={}", record.getId(), record.getTitle());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to create LLM chat record asynchronously", e);
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    private List<LlmChatMessage> copyMessages(List<LlmChatMessage> messages) {
        if (messages == null) {
            return Collections.emptyList();
        }
        return messages.stream()
                .filter(message -> message != null)
                .map(message -> new LlmChatMessage(message.getRole(), message.getContent()))
                .toList();
    }

    private String generateChatTitle(String summary, String transcript) {
        LlmChatRequest request = new LlmChatRequest();
        request.setTemperature(0.1);
        request.setMaxTokens(300);
        request.setMessages(List.of(
                new LlmChatMessage("system", "Create a one-line short title for this chat record. Do not think step by step. Return only the title, no quotes, no markdown, no punctuation at the end."),
                new LlmChatMessage("user", truncate("Summary:\n%s\n\nTranscript:\n%s".formatted(summary, transcript), 8000))
        ));
        LlmChatResponse response = marketPulseApiService.chatWithLlm(request);
        String title = extractTitle(response.getReply());
        if (title.isBlank()) {
            title = deriveTitleFromText(summary, transcript);
        }
        return truncate(title.replaceAll("\\s+", " "), 90);
    }

    private String summarizeChat(String transcript) {
        LlmChatRequest request = new LlmChatRequest();
        request.setTemperature(0.2);
        request.setMaxTokens(700);
        request.setMessages(List.of(
                new LlmChatMessage("system", "Summarize this chat as a concise record. Include decisions, facts, and next actions when present."),
                new LlmChatMessage("user", truncate(transcript, 10000))
        ));
        LlmChatResponse response = marketPulseApiService.chatWithLlm(request);
        return nullToBlank(response.getReply()).isBlank() ? "No summary generated." : response.getReply().trim();
    }

    private String buildTranscript(List<LlmChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .filter(message -> message != null && message.getContent() != null && !message.getContent().isBlank())
                .filter(message -> !"system".equalsIgnoreCase(nullToBlank(message.getRole())))
                .map(message -> "%s: %s".formatted(nullToBlank(message.getRole()).toUpperCase(Locale.ROOT), message.getContent().trim()))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private List<String> parseLabels(String reply, int limit) {
        String raw = stripReasoningAndMarkdown(nullToBlank(reply));
        if (raw.isBlank()) {
            return Collections.emptyList();
        }
        try {
            int start = raw.indexOf('[');
            int end = raw.lastIndexOf(']');
            if (start >= 0 && end > start) {
                return objectMapper.readValue(raw.substring(start, end + 1), new TypeReference<>() {});
            }
        } catch (Exception ignored) {
        }

        List<String> quotedLabels = extractQuotedLabels(raw);
        if (!quotedLabels.isEmpty()) {
            return quotedLabels;
        }

        String cleaned = raw.replace("`", "").replace("[", "").replace("]", "");
        List<String> labels = new ArrayList<>();
        for (String part : cleaned.split("[,\\n]")) {
            String label = part.replaceFirst("^[\\s\\-\\d.]+", "").replace("\"", "").trim();
            if (!label.isBlank()) {
                labels.add(label);
            }
        }
        if (normalizeLabels(labels, limit).isEmpty()) {
            labels.addAll(extractLabelTokens(raw, limit));
        }
        return labels;
    }

    private String stripReasoningAndMarkdown(String reply) {
        String withoutThinkBlocks = THINK_BLOCK_PATTERN.matcher(reply).replaceAll(" ");
        return withoutThinkBlocks
                .replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();
    }

    private String extractTitle(String reply) {
        String raw = stripReasoningAndMarkdown(nullToBlank(reply));
        if (raw.isBlank()) {
            return "";
        }
        String[] lines = raw.split("\\R+");
        for (String line : lines) {
            String cleaned = cleanTitleLine(line);
            if (!cleaned.isBlank() && !looksLikeInstruction(cleaned)) {
                return cleaned;
            }
        }
        return cleanTitleLine(raw);
    }

    private String cleanTitleLine(String line) {
        return nullToBlank(line)
                .replaceFirst("(?i)^\\s*(title|final answer|answer)\\s*[:\\-]\\s*", "")
                .replaceAll("^[\\s\\-#*`\"']+", "")
                .replaceAll("[\\s`\"']+$", "")
                .replaceAll("[.!?:;]+$", "")
                .trim();
    }

    private boolean looksLikeInstruction(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        return upper.startsWith("SURE")
                || upper.startsWith("HERE")
                || upper.contains("JSON ARRAY")
                || upper.contains("ONE-LINE")
                || upper.contains("SHORT TITLE");
    }

    private String deriveTitleFromText(String summary, String transcript) {
        String source = nullToBlank(summary).isBlank() ? nullToBlank(transcript) : nullToBlank(summary);
        for (String line : source.split("\\R+")) {
            String cleaned = cleanTitleLine(line);
            if (!cleaned.isBlank() && cleaned.length() >= 8) {
                return cleaned;
            }
        }
        List<String> labels = deriveLabelsFromText(source, 4);
        if (!labels.isEmpty()) {
            return String.join(" ", labels).replace("_", " ");
        }
        return "LLM Chat Summary";
    }

    private List<String> extractQuotedLabels(String raw) {
        List<String> labels = new ArrayList<>();
        Matcher matcher = JSON_STRING_PATTERN.matcher(raw);
        while (matcher.find()) {
            labels.add(matcher.group(1));
        }
        return labels;
    }

    private List<String> extractLabelTokens(String raw, int limit) {
        List<String> labels = new ArrayList<>();
        Matcher matcher = LABEL_TOKEN_PATTERN.matcher(raw);
        while (matcher.find() && labels.size() < limit * 3) {
            labels.add(matcher.group());
        }
        return labels;
    }

    private List<String> normalizeLabels(List<String> labels, int limit) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String label : labels) {
            String value = nullToBlank(label)
                    .trim()
                    .toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
            if (value.isBlank() || value.length() > 30 || isGenericLabel(value)) {
                continue;
            }
            normalized.add(value);
            if (normalized.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> deriveLabelsFromText(String text, int limit) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        Matcher matcher = LABEL_TOKEN_PATTERN.matcher(nullToBlank(text));
        while (matcher.find() && labels.size() < limit) {
            String value = matcher.group()
                    .toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
            if (value.length() >= 3 && value.length() <= 30 && !isGenericLabel(value)) {
                labels.add(value);
            }
        }
        return new ArrayList<>(labels);
    }

    private boolean isGenericLabel(String value) {
        return GENERIC_LABELS.contains(value);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return nullToBlank(value);
        }
        return value.substring(0, maxLength);
    }
}
