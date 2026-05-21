package com.yipeng.recorder.job;

import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.repository.JobExecutionRepository;
import com.yipeng.recorder.response.OptionSymbolsResponse;
import com.yipeng.recorder.service.MarketPulseApiService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OptionPartitionCheckJobHandler implements JobHandler {

    private static final String COMBINE_JOB_KEY = "combine-expired-option-parquet-sat-2130";

    private final MarketPulseApiService marketPulseApiService;
    private final JobExecutionRepository jobExecutionRepository;

    public OptionPartitionCheckJobHandler(MarketPulseApiService marketPulseApiService,
                                          JobExecutionRepository jobExecutionRepository) {
        this.marketPulseApiService = marketPulseApiService;
        this.jobExecutionRepository = jobExecutionRepository;
    }

    @Override
    public String jobType() {
        return JobType.OPTION_PARTITION_CHECK;
    }

    @Override
    public JobResult run(JobContext context) {
        OptionSymbolsResponse symbolsResponse = marketPulseApiService.getOptionSymbols();
        Map<String, Object> latestCombineExecution = new LinkedHashMap<>();
        jobExecutionRepository.findFirstByJobKeyOrderByCreatedAtDesc(COMBINE_JOB_KEY).ifPresent(execution -> {
            latestCombineExecution.put("id", execution.getId());
            latestCombineExecution.put("status", execution.getStatus().name());
            latestCombineExecution.put("finishedAt", execution.getFinishedAt() == null ? null : execution.getFinishedAt().toString());
            latestCombineExecution.put("summary", execution.getSummary());
        });

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("checkedAt", java.time.ZonedDateTime.now().toString());
        details.put("optionSymbolCount", symbolsResponse.getSymbols().size());
        details.put("latestCombineExecution", latestCombineExecution);
        return JobResult.of("Checked option partition status for %d option symbols.".formatted(
                symbolsResponse.getSymbols().size()
        ), details);
    }
}
