package com.yipeng.recorder.job;

import java.util.Set;

public final class JobType {

    public static final String MARKET_PULSE_HEALTH_CHECK = "MARKET_PULSE_HEALTH_CHECK";
    public static final String LLM_HEALTH_CHECK = "LLM_HEALTH_CHECK";
    public static final String SERVER_STATUS_EMAIL = "SERVER_STATUS_EMAIL";
    public static final String STOCK_OPTION_DATA_UPDATE = "STOCK_OPTION_DATA_UPDATE";
    public static final String STOCK_DAILY_HISTORY_UPDATE = "STOCK_DAILY_HISTORY_UPDATE";
    public static final String STOCK_AFTER_CLOSE_REFRESH = "STOCK_AFTER_CLOSE_REFRESH";
    public static final String MARKET_NEWS_SUMMARY_RECORD = "MARKET_NEWS_SUMMARY_RECORD";
    public static final String COMBINE_EXPIRED_OPTION_PARQUET = "COMBINE_EXPIRED_OPTION_PARQUET";
    public static final String JOB_EXECUTION_CLEANUP = "JOB_EXECUTION_CLEANUP";
    public static final String STOCK_DATA_FRESHNESS_CHECK = "STOCK_DATA_FRESHNESS_CHECK";
    public static final String OPTION_DATA_FRESHNESS_CHECK = "OPTION_DATA_FRESHNESS_CHECK";
    public static final String OPTION_PARTITION_CHECK = "OPTION_PARTITION_CHECK";
    public static final String QDRANT_RECORD_UPSERT = "QDRANT_RECORD_UPSERT";
    public static final String QDRANT_RECORD_DELETE = "QDRANT_RECORD_DELETE";
    public static final String QDRANT_CONSISTENCY_CHECK = "QDRANT_CONSISTENCY_CHECK";

    private static final Set<String> STATUS_CHECK_TYPES = Set.of(
            MARKET_PULSE_HEALTH_CHECK,
            LLM_HEALTH_CHECK,
            STOCK_DATA_FRESHNESS_CHECK,
            OPTION_DATA_FRESHNESS_CHECK,
            OPTION_PARTITION_CHECK,
            QDRANT_CONSISTENCY_CHECK
    );

    private JobType() {
    }

    public static boolean isStatusCheckType(String jobType) {
        return STATUS_CHECK_TYPES.contains(jobType);
    }
}
