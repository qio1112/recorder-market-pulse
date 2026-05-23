package com.yipeng.recorder.job;

import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.repository.JobExecutionRepository;
import com.yipeng.recorder.repository.RecordRepository;
import com.yipeng.recorder.response.LlmChatResponse;
import com.yipeng.recorder.response.OptionExpiryDatesResponse;
import com.yipeng.recorder.response.OptionExpiryResponse;
import com.yipeng.recorder.response.OptionSymbolsResponse;
import com.yipeng.recorder.service.MarketPulseApiService;
import com.yipeng.recorder.service.QdrantEmbeddingService;
import com.yipeng.recorder.service.StockDailyHistoryService;
import com.yipeng.recorder.utils.JobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardStatusJobHandlerTests {

    private static final JobContext CONTEXT = new JobContext(null, null, java.util.Map.of(), null, null);

    @Test
    void marketPulseHealthCheckReturnsStatusDetails() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        when(marketPulseApiService.getMarketPulseServerStatus()).thenReturn(ResponseEntity.ok("healthy"));

        JobResult result = new MarketPulseHealthCheckJobHandler(marketPulseApiService).run(CONTEXT);

        assertEquals(200, result.details().get("statusCode"));
        assertEquals("healthy", result.details().get("body"));
        assertTrue(result.summary().contains("HTTP 200"));
    }

    @Test
    void llmHealthCheckReturnsReplyDetails() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        when(marketPulseApiService.chatWithLlm(any())).thenReturn(new LlmChatResponse("OK"));

        JobResult result = new LlmHealthCheckJobHandler(marketPulseApiService).run(CONTEXT);

        assertEquals("OK", result.details().get("reply"));
        assertEquals("LLM health check completed.", result.summary());
    }

    @Test
    void stockFreshnessCheckUsesTradeDayAndCountsStaleSymbols() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        StockDailyHistoryService stockDailyHistoryService = mock(StockDailyHistoryService.class);
        when(marketPulseApiService.isTodayTradeDay()).thenReturn(true);
        when(marketPulseApiService.getTrackedSymbols(false)).thenReturn(List.of("AAPL", "MSFT", "NVDA"));
        when(stockDailyHistoryService.getLatestDateBySymbol("AAPL")).thenReturn(LocalDate.now());
        when(stockDailyHistoryService.getLatestDateBySymbol("MSFT")).thenReturn(LocalDate.now().minusDays(1));
        when(stockDailyHistoryService.getLatestDateBySymbol("NVDA")).thenReturn(null);

        JobResult result = new StockDataFreshnessCheckJobHandler(marketPulseApiService, stockDailyHistoryService).run(CONTEXT);

        assertEquals(true, result.details().get("todayTradeDay"));
        assertEquals(3, result.details().get("trackedSymbolCount"));
        assertEquals(1, result.details().get("missingCount"));
        assertEquals(1, result.details().get("staleCount"));
    }

    @Test
    void optionFreshnessCheckUsesLatestSuccessfulAfterCloseRefresh() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
        JobExecution execution = new JobExecution();
        execution.setId(42L);
        execution.setFinishedAt(ZonedDateTime.now());
        when(marketPulseApiService.isTodayTradeDay()).thenReturn(true);
        when(jobExecutionRepository.findFirstByJobTypeAndStatusOrderByFinishedAtDesc(
                JobType.STOCK_AFTER_CLOSE_REFRESH,
                JobStatus.SUCCESS
        )).thenReturn(Optional.of(execution));
        when(marketPulseApiService.getOptionSymbols()).thenReturn(new OptionSymbolsResponse(List.of("MSFT", "AAPL")));
        when(marketPulseApiService.getOptionExpiryDates("AAPL")).thenReturn(new OptionExpiryDatesResponse(
                "AAPL",
                List.of(new OptionExpiryResponse("2026-05-22", false), new OptionExpiryResponse("2026-05-29", false))
        ));
        when(marketPulseApiService.getOptionExpiryDates("MSFT")).thenReturn(new OptionExpiryDatesResponse(
                "MSFT",
                List.of(new OptionExpiryResponse("2026-05-22", false))
        ));

        JobResult result = new OptionDataFreshnessCheckJobHandler(marketPulseApiService, jobExecutionRepository).run(CONTEXT);

        assertEquals(true, result.details().get("todayTradeDay"));
        assertEquals(42L, result.details().get("latestSuccessfulAfterCloseRefreshExecutionId"));
        assertEquals(2, result.details().get("optionSymbolCount"));
        assertEquals(3, result.details().get("optionTotalExpiryCount"));
        assertEquals(Map.of("AAPL", 2, "MSFT", 1), result.details().get("optionExpiryCountBySymbol"));
    }

    @Test
    void optionFreshnessCheckFailsWhenTradeDayRefreshIsStale() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
        JobExecution execution = new JobExecution();
        execution.setId(43L);
        execution.setFinishedAt(ZonedDateTime.now().minusDays(1));
        when(marketPulseApiService.isTodayTradeDay()).thenReturn(true);
        when(jobExecutionRepository.findFirstByJobTypeAndStatusOrderByFinishedAtDesc(
                JobType.STOCK_AFTER_CLOSE_REFRESH,
                JobStatus.SUCCESS
        ))
                .thenReturn(Optional.of(execution));
        when(marketPulseApiService.getOptionSymbols()).thenReturn(new OptionSymbolsResponse(List.of("AAPL")));
        when(marketPulseApiService.getOptionExpiryDates("AAPL")).thenReturn(new OptionExpiryDatesResponse(
                "AAPL",
                List.of(new OptionExpiryResponse("2026-05-22", false))
        ));

        JobCheckException exception = assertThrows(
                JobCheckException.class,
                () -> new OptionDataFreshnessCheckJobHandler(marketPulseApiService, jobExecutionRepository).run(CONTEXT)
        );

        assertTrue(exception.getMessage().contains("stale"));
        assertEquals(43L, exception.getDetails().get("latestSuccessfulAfterCloseRefreshExecutionId"));
        assertEquals(Map.of("AAPL", 1), exception.getDetails().get("optionExpiryCountBySymbol"));
    }

    @Test
    void qdrantConsistencyCheckStoresCountLevelStatus() {
        RecordRepository recordRepository = mock(RecordRepository.class);
        JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
        QdrantEmbeddingService qdrantEmbeddingService = mock(QdrantEmbeddingService.class);
        JobExecution failedUpsert = new JobExecution();
        failedUpsert.setJobType(JobType.QDRANT_RECORD_UPSERT);
        failedUpsert.setStatus(JobStatus.FAILED);
        when(recordRepository.findAllRecordIds()).thenReturn(List.of(1L, 2L, 3L));
        when(qdrantEmbeddingService.listRecordIds()).thenReturn(Set.of("1", "3", "99"));
        when(jobExecutionRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(failedUpsert));

        JobResult result = new QdrantConsistencyCheckJobHandler(
                recordRepository,
                jobExecutionRepository,
                qdrantEmbeddingService
        ).run(CONTEXT);

        assertEquals(3, result.details().get("recordCount"));
        assertEquals(3, result.details().get("checkedCount"));
        assertEquals(1, result.details().get("missingCount"));
        assertEquals(1, result.details().get("staleCount"));
        assertEquals(0, result.details().get("failedCheckCount"));
        assertEquals(1L, result.details().get("failedUpsertDeleteCount"));
    }
}
