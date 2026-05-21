package com.yipeng.recorder.job;

import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.repository.JobExecutionRepository;
import com.yipeng.recorder.response.OptionExpiryDatesResponse;
import com.yipeng.recorder.response.OptionSymbolsResponse;
import com.yipeng.recorder.service.MarketPulseApiService;
import com.yipeng.recorder.utils.JobStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OptionDataFreshnessCheckJobHandler implements JobHandler {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of(BuiltInJobDefinitions.DEFAULT_TIMEZONE);

    private final MarketPulseApiService marketPulseApiService;
    private final JobExecutionRepository jobExecutionRepository;

    public OptionDataFreshnessCheckJobHandler(MarketPulseApiService marketPulseApiService,
                                              JobExecutionRepository jobExecutionRepository) {
        this.marketPulseApiService = marketPulseApiService;
        this.jobExecutionRepository = jobExecutionRepository;
    }

    @Override
    public String jobType() {
        return JobType.OPTION_DATA_FRESHNESS_CHECK;
    }

    @Override
    public JobResult run(JobContext context) {
        boolean todayTradeDay = marketPulseApiService.isTodayTradeDay();
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        Optional<JobExecution> latestRefresh = jobExecutionRepository.findFirstByJobTypeAndStatusOrderByFinishedAtDesc(
                JobType.STOCK_AFTER_CLOSE_REFRESH,
                JobStatus.SUCCESS
        );
        LocalDate latestRefreshDate = latestRefresh
                .map(JobExecution::getFinishedAt)
                .map(finishedAt -> finishedAt.withZoneSameInstant(DEFAULT_ZONE).toLocalDate())
                .orElse(null);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("checkedAt", ZonedDateTime.now(DEFAULT_ZONE).toString());
        details.put("todayTradeDay", todayTradeDay);
        details.put("expectedDate", today.toString());
        details.put("latestSuccessfulAfterCloseRefreshExecutionId", latestRefresh.map(JobExecution::getId).orElse(null));
        details.put("latestSuccessfulAfterCloseRefreshFinishedAt", latestRefresh
                .map(JobExecution::getFinishedAt)
                .map(ZonedDateTime::toString)
                .orElse(null));
        details.put("latestSuccessfulAfterCloseRefreshDate", latestRefreshDate == null ? null : latestRefreshDate.toString());
        details.putAll(buildOptionExpirySummary());

        if (todayTradeDay && (latestRefreshDate == null || latestRefreshDate.isBefore(today))) {
            throw new JobCheckException(
                    "Option data is stale: latest successful after-close refresh is not from today's trade date.",
                    details
            );
        }

        return JobResult.of("Checked option freshness against latest successful after-close refresh.", details);
    }

    private Map<String, Object> buildOptionExpirySummary() {
        OptionSymbolsResponse symbolsResponse = marketPulseApiService.getOptionSymbols();
        List<String> rawSymbols = symbolsResponse.getSymbols() == null ? List.of() : symbolsResponse.getSymbols();
        List<String> symbols = rawSymbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .sorted(Comparator.naturalOrder())
                .toList();

        Map<String, Integer> expiryCountBySymbol = new LinkedHashMap<>();
        int totalExpiryCount = 0;
        for (String symbol : symbols) {
            OptionExpiryDatesResponse expiryDatesResponse = marketPulseApiService.getOptionExpiryDates(symbol);
            int expiryCount = expiryDatesResponse.getExpiryDates().size();
            expiryCountBySymbol.put(symbol, expiryCount);
            totalExpiryCount += expiryCount;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("optionSymbolCount", symbols.size());
        summary.put("optionTotalExpiryCount", totalExpiryCount);
        summary.put("optionExpiryCountBySymbol", expiryCountBySymbol);
        return summary;
    }
}
