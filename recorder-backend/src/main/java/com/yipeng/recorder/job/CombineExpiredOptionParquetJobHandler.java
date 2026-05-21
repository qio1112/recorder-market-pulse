package com.yipeng.recorder.job;

import com.yipeng.recorder.service.CronService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CombineExpiredOptionParquetJobHandler implements JobHandler {

    private final CronService cronService;

    public CombineExpiredOptionParquetJobHandler(CronService cronService) {
        this.cronService = cronService;
    }

    @Override
    public String jobType() {
        return JobType.COMBINE_EXPIRED_OPTION_PARQUET;
    }

    @Override
    public JobResult run(JobContext context) {
        String timeName = getStringParam(context, "timeName", "manual");
        cronService.combineExpiredOptionParquetFilesJob(timeName);
        return JobResult.of("Expired option parquet combine job completed.", Map.of("timeName", timeName));
    }

    private String getStringParam(JobContext context, String key, String defaultValue) {
        Object value = context.parameters().get(key);
        return value == null ? defaultValue : value.toString();
    }
}
