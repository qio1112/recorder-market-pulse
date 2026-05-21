package com.yipeng.recorder.job;

import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.service.CronService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MarketNewsSummaryRecordJobHandler implements JobHandler {

    private final CronService cronService;

    public MarketNewsSummaryRecordJobHandler(CronService cronService) {
        this.cronService = cronService;
    }

    @Override
    public String jobType() {
        return JobType.MARKET_NEWS_SUMMARY_RECORD;
    }

    @Override
    public JobResult run(JobContext context) {
        List<Record> records = cronService.createManualMarketNewsSummaryRecord();
        List<Long> recordIds = records.stream().map(Record::getId).toList();
        return JobResult.of(
                "Market news summary records created: " + recordIds.size(),
                Map.of("createdRecordIds", recordIds)
        );
    }
}
