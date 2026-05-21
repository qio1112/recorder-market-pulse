package com.yipeng.recorder.job;

import com.yipeng.recorder.utils.JobScheduleType;

import java.util.List;

public final class BuiltInJobDefinitions {

    public static final String DEFAULT_TIMEZONE = "America/New_York";

    private BuiltInJobDefinitions() {
    }

    public static List<BuiltInJobDefinition> all() {
        return List.of(
                cron("stock-option-update-1005", "Update stock option data 10:05", JobType.STOCK_OPTION_DATA_UPDATE,
                        "0 5 10 * * MON-FRI", "{\"timeName\":\"10:05\"}", 300L, 1, 300L,
                        "Calls Market Pulse stock/option update at 10:05 on weekdays."),
                cron("stock-option-update-1330", "Update stock option data 13:30", JobType.STOCK_OPTION_DATA_UPDATE,
                        "0 30 13 * * MON-FRI", "{\"timeName\":\"13:30\"}", 300L, 1, 300L,
                        "Calls Market Pulse stock/option update at 13:30 on weekdays."),
                cron("stock-after-close-refresh-1630", "Stock data jobs 16:30", JobType.STOCK_AFTER_CLOSE_REFRESH,
                        "0 30 16 * * MON-FRI", "{\"timeName\":\"16:30\"}", 600L, 1, 300L,
                        "Runs stock/option update plus backend daily-history refresh at 16:30 on weekdays."),
                cron("stock-after-close-refresh-2100", "Stock data jobs 21:00", JobType.STOCK_AFTER_CLOSE_REFRESH,
                        "0 0 21 * * MON-FRI", "{\"timeName\":\"21:00\"}", 600L, 1, 300L,
                        "Runs stock/option update plus backend daily-history refresh at 21:00 on weekdays."),
                cron("market-news-summary-weekday-2130", "Market news summary 21:30", JobType.MARKET_NEWS_SUMMARY_RECORD,
                        "0 30 21 * * MON-FRI", "{\"timeName\":\"21:30\"}", 900L, 1, 300L,
                        "Creates public market-news summary records on weekdays."),
                cron("server-status-0800", "Status update 08:00", JobType.SERVER_STATUS_EMAIL,
                        "0 0 8 * * *", "{\"timeName\":\"08:00\"}", 120L, 0, 0L,
                        "Sends server status email at 08:00."),
                cron("server-status-1200", "Status update 12:00", JobType.SERVER_STATUS_EMAIL,
                        "0 0 12 * * *", "{\"timeName\":\"12:00\"}", 120L, 0, 0L,
                        "Sends server status email at 12:00."),
                cron("server-status-1600", "Status update 16:00", JobType.SERVER_STATUS_EMAIL,
                        "0 0 16 * * *", "{\"timeName\":\"16:00\"}", 120L, 0, 0L,
                        "Sends server status email at 16:00."),
                cron("server-status-2000", "Status update 20:00", JobType.SERVER_STATUS_EMAIL,
                        "0 0 20 * * *", "{\"timeName\":\"20:00\"}", 120L, 0, 0L,
                        "Sends server status email at 20:00."),
                cron("server-status-2300", "Status update 23:00", JobType.SERVER_STATUS_EMAIL,
                        "0 0 23 * * *", "{\"timeName\":\"23:00\"}", 120L, 0, 0L,
                        "Sends server status email at 23:00."),
                cron("combine-expired-option-parquet-sat-2130", "Combine expired option parquet files Saturday 21:30", JobType.COMBINE_EXPIRED_OPTION_PARQUET,
                        "0 30 21 * * SAT", "{\"timeName\":\"Saturday 21:30\"}", 900L, 1, 300L,
                        "Combines expired option parquet files weekly."),
                fixed("market-pulse-health-check", "Market Pulse health check", JobType.MARKET_PULSE_HEALTH_CHECK,
                        300L, "{}", 60L, 1, 60L,
                        "Checks the Market Pulse /health endpoint every five minutes."),
                cron("stock-data-freshness-check", "Stock data freshness check", JobType.STOCK_DATA_FRESHNESS_CHECK,
                        "0 0 22 * * *", "{}", 120L, 0, 0L,
                        "Checks tracked stock symbols against backend daily-history freshness daily at 22:00."),
                cron("option-data-freshness-check", "Option data freshness check", JobType.OPTION_DATA_FRESHNESS_CHECK,
                        "0 0 22 * * *", "{}", 180L, 0, 0L,
                        "Fetches current option symbols and expiry-date counts from Market Pulse daily at 22:00."),
                cron("qdrant-consistency-check", "Qdrant consistency check", JobType.QDRANT_CONSISTENCY_CHECK,
                        "0 0 22 * * *", "{}", 600L, 0, 0L,
                        "Checks count-level consistency between backend records and Qdrant daily at 22:00."),
                manual("qdrant-record-upsert", "Qdrant record upsert", JobType.QDRANT_RECORD_UPSERT,
                        "{}", 120L, 0, 0L,
                        "Durable internal job for upserting one record into Qdrant."),
                manual("qdrant-record-delete", "Qdrant record delete", JobType.QDRANT_RECORD_DELETE,
                        "{}", 120L, 0, 0L,
                        "Durable internal job for deleting one record from Qdrant."),
                manual("llm-health-check", "LLM health check", JobType.LLM_HEALTH_CHECK,
                        "{}", 60L, 0, 0L,
                        "Manual LLM connectivity check. Scheduled behavior can be enabled later."),
                cron("job-execution-cleanup", "Job execution cleanup", JobType.JOB_EXECUTION_CLEANUP,
                        "0 0 23 * * *", "{}", 120L, 0, 0L,
                        "Deletes job execution rows after the configured retention window daily at 23:00.")
        );
    }

    private static BuiltInJobDefinition cron(String key, String name, String type, String cron, String params,
                                             Long maxRuntimeSeconds, int retryCount, long retryDelaySeconds,
                                             String description) {
        return new BuiltInJobDefinition(key, name, type, true, JobScheduleType.CRON, cron, null,
                DEFAULT_TIMEZONE, params, maxRuntimeSeconds, retryCount, retryDelaySeconds, false, 1, description);
    }

    private static BuiltInJobDefinition fixed(String key, String name, String type, Long intervalSeconds, String params,
                                              Long maxRuntimeSeconds, int retryCount, long retryDelaySeconds,
                                              String description) {
        return new BuiltInJobDefinition(key, name, type, true, JobScheduleType.FIXED_INTERVAL, null, intervalSeconds,
                DEFAULT_TIMEZONE, params, maxRuntimeSeconds, retryCount, retryDelaySeconds, false, 1, description);
    }

    private static BuiltInJobDefinition manual(String key, String name, String type, String params,
                                               Long maxRuntimeSeconds, int retryCount, long retryDelaySeconds,
                                               String description) {
        return new BuiltInJobDefinition(key, name, type, true, JobScheduleType.MANUAL, null, null,
                DEFAULT_TIMEZONE, params, maxRuntimeSeconds, retryCount, retryDelaySeconds, false, 1, description);
    }
}
