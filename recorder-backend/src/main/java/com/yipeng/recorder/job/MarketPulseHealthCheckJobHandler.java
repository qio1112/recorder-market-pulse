package com.yipeng.recorder.job;

import com.yipeng.recorder.service.MarketPulseApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MarketPulseHealthCheckJobHandler implements JobHandler {

    private final MarketPulseApiService marketPulseApiService;

    public MarketPulseHealthCheckJobHandler(MarketPulseApiService marketPulseApiService) {
        this.marketPulseApiService = marketPulseApiService;
    }

    @Override
    public String jobType() {
        return JobType.MARKET_PULSE_HEALTH_CHECK;
    }

    @Override
    public JobResult run(JobContext context) {
        long started = System.currentTimeMillis();
        ResponseEntity<String> response = marketPulseApiService.getMarketPulseServerStatus();
        long latencyMs = System.currentTimeMillis() - started;
        return JobResult.of(
                "Market Pulse health check returned HTTP " + response.getStatusCode().value(),
                Map.of(
                        "statusCode", response.getStatusCode().value(),
                        "latencyMs", latencyMs,
                        "body", response.getBody() == null ? "" : response.getBody()
                )
        );
    }
}
