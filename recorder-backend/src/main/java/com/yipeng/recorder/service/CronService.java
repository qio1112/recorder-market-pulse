package com.yipeng.recorder.service;

import com.yipeng.recorder.model.StockDailyHistory;
import com.yipeng.recorder.response.OptionParquetCombineResponse;
import com.yipeng.recorder.utils.DateTimeUtils;
import com.yipeng.recorder.utils.IPUtil;
import jakarta.mail.MessagingException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CronService {

    private static final Logger logger = LoggerFactory.getLogger(CronService.class);

    @Value("${admin.email}")
    private String adminEmail;

    private final SendEmailService sendEmailService;
    private final DateTimeUtils dateTimeUtils;
    private final MarketPulseApiService marketPulseApiService;
    private final StockDailyHistoryService stockDailyHistoryService;

    @Autowired
    public CronService(SendEmailService sendEmailService,
                       DateTimeUtils dateTimeUtils,
                       MarketPulseApiService marketPulseApiService,
                       StockDailyHistoryService stockDailyHistoryService) {
        this.sendEmailService = sendEmailService;
        this.dateTimeUtils = dateTimeUtils;
        this.marketPulseApiService = marketPulseApiService;
        this.stockDailyHistoryService = stockDailyHistoryService;
    }

    @Scheduled(cron = "0 5 10 * * *")
    public void runUpdateStockTask1005() {
        updateStockOptionDataJob("10:05");
    }

    @Scheduled(cron = "0 30 13 * * *")
    public void runUpdateStockTask1330() {
        updateStockOptionDataJob("13:30");
    }

    @Scheduled(cron = "0 30 16 * * *")
    public void runUpdateStockTask1630() {
        updateStockOptionDataJob("16:30");
        updateStockDailyHistory("16:30");
    }

    @Scheduled(cron = "0 0 21 * * *")
    public void runUpdateStockTask2100() {
        updateStockOptionDataJob("21:00");
        updateStockDailyHistory("21:00");
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void runStatusUpdate0800() {
        serverStatusEmail("08:00");
    }

    @Scheduled(cron = "0 0 12 * * *")
    public void runStatusUpdate1200() {
        serverStatusEmail("12:00");
    }

    @Scheduled(cron = "0 0 16 * * *")
    public void runStatusUpdate1600() {
        serverStatusEmail("16:00");
    }

    @Scheduled(cron = "0 0 20 * * *")
    public void runStatusUpdate2000() {
        serverStatusEmail("20:00");
    }

    @Scheduled(cron = "0 0 23 * * *")
    public void runStatusUpdate2300() {
        serverStatusEmail("23:00");
    }

    @Scheduled(cron = "0 30 21 * * SAT")
    public void runCombineExpiredOptionParquetFiles2130Friday() {
        try {
            combineExpiredOptionParquetFilesJob("Friday 21:30");
        } catch (Exception e) {
            logger.error("Failed to combine expired option parquet files for Friday 21:30", e);
            sendTaskEmail(
                    "FAILED: Combine expired option parquet files Friday 21:30",
                    "Failed to combine expired option parquet files.\n\nError: " + e.getMessage()
            );
            throw e;
        }
    }

    public void updateStockDailyHistory(String timeName) {
        String today = dateTimeUtils.getCurrentDateString();
        String fullTimeName = today + " " + timeName;
        Map<String, List<StockDailyHistory>> stockDailyHistory = this.marketPulseApiService.getStockDailyHistory(null); // use default tracked symbols
        this.stockDailyHistoryService.updateStockDailyHistoryDatabase(stockDailyHistory);
        logger.info("Updated daily stock data: " + fullTimeName + "\n symbols: " + StringUtils.join(stockDailyHistory.keySet(), ","));
        this.sendTaskEmail("Update stock daily history " + fullTimeName, "Update stock daily history for symbols: " + StringUtils.join(stockDailyHistory.keySet(), ","));
    }


    public void updateStockOptionDataJob(String timeName) {
        String today = dateTimeUtils.getCurrentDateString();
        String fullTimeName = today + " " + timeName;
        String response = this.marketPulseApiService.runUpdateStockOptionDataApi(null);
        logger.info("Updated option data: " + fullTimeName + "\n" + response);
        this.sendTaskEmail("Updated option data " + fullTimeName, response);
    }

    public void combineExpiredOptionParquetFilesJob(String timeName) {
        String today = dateTimeUtils.getCurrentDateString();
        String fullTimeName = today + " " + timeName;
        OptionParquetCombineResponse response = this.marketPulseApiService.combineExpiredOptionParquetFiles();
        logger.info("Combined expired option parquet files: {}. Combined count: {}",
                fullTimeName,
                response == null ? null : response.getCombinedCount());
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
