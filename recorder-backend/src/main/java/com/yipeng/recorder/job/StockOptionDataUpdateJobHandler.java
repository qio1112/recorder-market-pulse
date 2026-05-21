package com.yipeng.recorder.job;

import com.yipeng.recorder.service.CronService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StockOptionDataUpdateJobHandler implements JobHandler {

    private final CronService cronService;

    public StockOptionDataUpdateJobHandler(CronService cronService) {
        this.cronService = cronService;
    }

    @Override
    public String jobType() {
        return JobType.STOCK_OPTION_DATA_UPDATE;
    }

    @Override
    public JobResult run(JobContext context) {
        String timeName = getStringParam(context, "timeName", "manual");
        CronService.StockJobResult result = cronService.updateStockOptionDataJob(timeName);
        cronService.sendStockJobEmail(timeName, java.util.List.of(result));
        return JobResult.of(result.name(), Map.of("timeName", timeName, "detail", result.detail()));
    }

    private String getStringParam(JobContext context, String key, String defaultValue) {
        Object value = context.parameters().get(key);
        return value == null ? defaultValue : value.toString();
    }
}
