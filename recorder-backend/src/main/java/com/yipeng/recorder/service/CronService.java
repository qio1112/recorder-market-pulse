package com.yipeng.recorder.service;

import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.StockDailyHistory;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.response.OptionParquetCombineResponse;
import com.yipeng.recorder.response.StockNewsSummaryResponse;
import com.yipeng.recorder.response.StockNewsSymbolSummaryResponse;
import com.yipeng.recorder.utils.DateTimeUtils;
import com.yipeng.recorder.utils.IPUtil;
import jakarta.mail.MessagingException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Service
public class CronService {

    private static final Logger logger = LoggerFactory.getLogger(CronService.class);
    private static final int MARKET_NEWS_SUMMARY_SYMBOLS_PER_RECORD = 3;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.username}")
    private String adminUsername;

    private final SendEmailService sendEmailService;
    private final DateTimeUtils dateTimeUtils;
    private final MarketPulseApiService marketPulseApiService;
    private final StockDailyHistoryService stockDailyHistoryService;
    private final UserService userService;
    private final RecordService recordService;
    private final QdrantEmbeddingService qdrantEmbeddingService;

    @Autowired
    public CronService(SendEmailService sendEmailService,
                       DateTimeUtils dateTimeUtils,
                       MarketPulseApiService marketPulseApiService,
                       StockDailyHistoryService stockDailyHistoryService,
                       UserService userService,
                       RecordService recordService,
                       QdrantEmbeddingService qdrantEmbeddingService) {
        this.sendEmailService = sendEmailService;
        this.dateTimeUtils = dateTimeUtils;
        this.marketPulseApiService = marketPulseApiService;
        this.stockDailyHistoryService = stockDailyHistoryService;
        this.userService = userService;
        this.recordService = recordService;
        this.qdrantEmbeddingService = qdrantEmbeddingService;
    }

    public StockJobResult updateStockDailyHistory(String timeName) {
        String today = dateTimeUtils.getCurrentDateString();
        String fullTimeName = today + " " + timeName;
        Map<String, List<StockDailyHistory>> stockDailyHistory = this.marketPulseApiService.getStockDailyHistory(null); // use default tracked symbols
        this.stockDailyHistoryService.updateStockDailyHistoryDatabase(stockDailyHistory);
        logger.info("Updated daily stock data: " + fullTimeName + "\n symbols: " + StringUtils.join(stockDailyHistory.keySet(), ","));
        return new StockJobResult(
                "Update stock daily history",
                "Update stock daily history for symbols: " + StringUtils.join(stockDailyHistory.keySet(), ",")
        );
    }


    public StockJobResult updateStockOptionDataJob(String timeName) {
        String today = dateTimeUtils.getCurrentDateString();
        String fullTimeName = today + " " + timeName;
        String response = this.marketPulseApiService.runUpdateStockOptionDataApi(null);
        logger.info("Updated option data: " + fullTimeName + "\n" + response);
        return new StockJobResult("Update stock option data", response);
    }

    public void sendStockJobEmail(String timeName, List<StockJobResult> results) {
        String today = dateTimeUtils.getCurrentDateString();
        String fullTimeName = today + " " + timeName;
        List<StockJobResult> nonNullResults = results.stream()
                .filter(result -> result != null)
                .toList();
        String jobNames = StringUtils.join(nonNullResults.stream().map(StockJobResult::name).toList(), ", ");
        String subject = nonNullResults.size() == 1
                ? nonNullResults.get(0).name() + " " + fullTimeName
                : "Stock data jobs " + fullTimeName + ": " + jobNames;
        String content = nonNullResults.stream()
                .map(result -> "%s\n%s".formatted(
                        result.name(),
                        StringUtils.isBlank(result.detail()) ? result.name() : result.detail()
                ))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(StringUtils.isBlank(jobNames) ? "Stock data jobs completed." : jobNames);
        this.sendTaskEmail(subject, content);
    }

    public record StockJobResult(String name, String detail) {
    }

    public void combineExpiredOptionParquetFilesJob(String timeName) {
        String today = dateTimeUtils.getCurrentDateString();
        String fullTimeName = today + " " + timeName;
        OptionParquetCombineResponse response = this.marketPulseApiService.combineExpiredOptionParquetFiles();
        logger.info("Combined expired option parquet files: {}. Combined count: {}",
                fullTimeName,
                response == null ? null : response.getCombinedCount());
    }

    public void createMarketNewsSummaryRecord(String timeName) {
        createMarketNewsSummaryRecord(timeName, false);
    }

    public List<Record> createManualMarketNewsSummaryRecord() {
        return createMarketNewsSummaryRecord("manual", true);
    }

    @Async
    public CompletableFuture<Void> createManualMarketNewsSummaryRecordAsync() {
        try {
            createManualMarketNewsSummaryRecord();
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to create manual market news summary record", e);
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    private List<Record> createMarketNewsSummaryRecord(String timeName, boolean includeTimestampInTitle) {
        String today = dateTimeUtils.getCurrentDateString();
        String fullTimeName = today + " " + timeName;
        User admin = userService.findByUsername(adminUsername);
        if (admin == null) {
            throw new IllegalStateException("Admin user not found: " + adminUsername);
        }

        StockNewsSummaryResponse response = marketPulseApiService.getTrackedStockNewsSummary();
        List<StockNewsSymbolSummaryResponse> summaries = getSortedMarketNewsSummaries(response);
        if (summaries.isEmpty()) {
            summaries = List.of(getEmptyMarketNewsSummary());
        }

        List<Record> savedRecords = new ArrayList<>();
        String titleBase = includeTimestampInTitle
                ? "Market News Summary " + dateTimeUtils.getCurrentDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : "Market News Summary " + today;

        for (int index = 0; index < summaries.size(); index += MARKET_NEWS_SUMMARY_SYMBOLS_PER_RECORD) {
            List<StockNewsSymbolSummaryResponse> chunk = summaries.subList(
                    index,
                    Math.min(index + MARKET_NEWS_SUMMARY_SYMBOLS_PER_RECORD, summaries.size())
            );
            List<String> symbols = chunk.stream().map(this::normalizeSymbol).filter(symbol -> !symbol.isBlank()).toList();
            String title = titleBase + " " + StringUtils.join(symbols, "-");
            String content = buildMarketNewsSummaryContent(response == null ? null : response.getGeneratedAt(), chunk);
            List<String> labels = new ArrayList<>(List.of("MARKET_NEWS_SUMMARY", "MARKET_PULSE", today));
            labels.addAll(symbols);
            Record record = new Record(title, admin, content, true);
            Record savedRecord = recordService.createRecord(
                    record,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    labels,
                    admin,
                    null,
                    true,
                    Map.of("source", "market_pulse", "summary_date", today, "symbols", StringUtils.join(symbols, ","))
            );
            qdrantEmbeddingService.upsertRecordAsync(savedRecord, admin);
            savedRecords.add(savedRecord);
            logger.info("Created market news summary record {} for {} symbols {}", savedRecord.getId(), fullTimeName, StringUtils.join(symbols, ","));
        }
        return savedRecords;
    }

    private List<StockNewsSymbolSummaryResponse> getSortedMarketNewsSummaries(StockNewsSummaryResponse response) {
        if (response == null || response.getSummaries() == null) {
            return new ArrayList<>();
        }
        return response.getSummaries().stream()
                .sorted((left, right) -> normalizeSymbol(left).compareTo(normalizeSymbol(right)))
                .toList();
    }

    private StockNewsSymbolSummaryResponse getEmptyMarketNewsSummary() {
        StockNewsSymbolSummaryResponse item = new StockNewsSymbolSummaryResponse();
        item.setSymbol("MARKET");
        item.setSummary("No market news summaries were returned.");
        item.setArticleCount(0);
        return item;
    }

    private String normalizeSymbol(StockNewsSymbolSummaryResponse item) {
        return item == null || item.getSymbol() == null ? "" : item.getSymbol().trim().toUpperCase();
    }

    private String buildMarketNewsSummaryContent(String generatedAt, List<StockNewsSymbolSummaryResponse> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return "No market news summaries were returned.";
        }
        StringBuilder sb = new StringBuilder();
        if (generatedAt != null) {
            sb.append("Generated at: ").append(generatedAt).append("\n\n");
        }
        for (StockNewsSymbolSummaryResponse item : summaries) {
            sb.append(normalizeSymbol(item)).append("\n");
            sb.append(item.getSummary() == null || item.getSummary().isBlank() ? "No summary available." : item.getSummary());
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    public void sendTaskEmail(String subject, String content) {
        try {
            sendEmailService.sendEmail(adminEmail, subject, content, null);
            logger.info("Email sent to adminEmail {} for {}", adminEmail, subject);
        } catch (MessagingException e) {
            logger.warn("Failed to send email to adminEmail {} for {}. Error: {}", adminEmail, subject, e.getMessage());
        }
    }

    public void serverStatusEmail(String timeName) {
        String publicIP = IPUtil.getPublicIP();
        String today = dateTimeUtils.getCurrentDateString();
        String fullTimeName = today + " " + timeName;
        String content = """
                Server is on.
                public ip: %s
                """.formatted(publicIP);
        this.sendTaskEmail("STATUS: Server is on " + fullTimeName, content);
    }
}
