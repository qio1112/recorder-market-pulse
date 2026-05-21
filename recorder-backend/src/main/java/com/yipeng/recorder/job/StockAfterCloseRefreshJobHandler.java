package com.yipeng.recorder.job;

import com.yipeng.recorder.service.CronService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StockAfterCloseRefreshJobHandler implements JobHandler {

    private final CronService cronService;

    public StockAfterCloseRefreshJobHandler(CronService cronService) {
        this.cronService = cronService;
    }

    @Override
    public String jobType() {
        return JobType.STOCK_AFTER_CLOSE_REFRESH;
    }

    @Override
    public JobResult run(JobContext context) {
        String timeName = getStringParam(context, "timeName", "manual");
        CronService.StockJobResult optionResult = cronService.updateStockOptionDataJob(timeName);
        CronService.StockJobResult historyResult = cronService.updateStockDailyHistory(timeName);
        cronService.sendStockJobEmail(timeName, java.util.List.of(optionResult, historyResult));
        return JobResult.of(
                "Stock after-close refresh completed.",
                Map.of(
                        "timeName", timeName,
                        "optionDataDetail", optionResult.detail(),
                        "dailyHistoryDetail", historyResult.detail()
                )
        );
    }

    private String getStringParam(JobContext context, String key, String defaultValue) {
        Object value = context.parameters().get(key);
        return value == null ? defaultValue : value.toString();
    }
}
