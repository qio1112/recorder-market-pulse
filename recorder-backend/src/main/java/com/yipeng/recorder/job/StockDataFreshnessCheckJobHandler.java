package com.yipeng.recorder.job;

import com.yipeng.recorder.service.MarketPulseApiService;
import com.yipeng.recorder.service.StockDailyHistoryService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StockDataFreshnessCheckJobHandler implements JobHandler {

    private final MarketPulseApiService marketPulseApiService;
    private final StockDailyHistoryService stockDailyHistoryService;

    public StockDataFreshnessCheckJobHandler(MarketPulseApiService marketPulseApiService,
                                             StockDailyHistoryService stockDailyHistoryService) {
        this.marketPulseApiService = marketPulseApiService;
        this.stockDailyHistoryService = stockDailyHistoryService;
    }

    @Override
    public String jobType() {
        return JobType.STOCK_DATA_FRESHNESS_CHECK;
    }

    @Override
    public JobResult run(JobContext context) {
        LocalDate today = LocalDate.now();
        boolean todayTradeDay = marketPulseApiService.isTodayTradeDay();
        List<String> trackedSymbols = marketPulseApiService.getTrackedSymbols(false);
        Map<String, String> latestTradeDates = new LinkedHashMap<>();
        int missingCount = 0;
        int staleCount = 0;

        for (String symbol : trackedSymbols) {
            LocalDate latestDate = stockDailyHistoryService.getLatestDateBySymbol(symbol);
            if (latestDate == null) {
                missingCount++;
                latestTradeDates.put(symbol, null);
            } else {
                latestTradeDates.put(symbol, latestDate.toString());
                if (todayTradeDay && latestDate.isBefore(today)) {
                    staleCount++;
                }
            }
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("checkedAt", java.time.ZonedDateTime.now().toString());
        details.put("today", today.toString());
        details.put("todayTradeDay", todayTradeDay);
        details.put("trackedSymbolCount", trackedSymbols.size());
        details.put("missingCount", missingCount);
        details.put("staleCount", staleCount);
        details.put("latestTradeDates", latestTradeDates);
        return JobResult.of("Checked stock freshness: %d tracked, %d missing, %d stale.".formatted(
                trackedSymbols.size(),
                missingCount,
                staleCount
        ), details);
    }
}
