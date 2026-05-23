package com.yipeng.recorder.job;

import com.yipeng.recorder.prompt.BuiltInLlmTokenLimits;
import com.yipeng.recorder.request.LlmChatMessage;
import com.yipeng.recorder.request.LlmChatRequest;
import com.yipeng.recorder.response.LlmChatResponse;
import com.yipeng.recorder.service.MarketPulseApiService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LlmHealthCheckJobHandler implements JobHandler {

    private final MarketPulseApiService marketPulseApiService;

    public LlmHealthCheckJobHandler(MarketPulseApiService marketPulseApiService) {
        this.marketPulseApiService = marketPulseApiService;
    }

    @Override
    public String jobType() {
        return JobType.LLM_HEALTH_CHECK;
    }

    @Override
    public JobResult run(JobContext context) {
        LlmChatRequest request = new LlmChatRequest();
        LlmChatMessage message = new LlmChatMessage();
        message.setRole("user");
        message.setContent("Reply with OK.");
        request.setMessages(List.of(message));
        request.setMaxTokens(BuiltInLlmTokenLimits.LLM_HEALTH_CHECK_MAX_TOKENS);
        long started = System.currentTimeMillis();
        LlmChatResponse response = marketPulseApiService.chatWithLlm(request);
        long latencyMs = System.currentTimeMillis() - started;
        return JobResult.of(
                "LLM health check completed.",
                Map.of(
                        "latencyMs", latencyMs,
                        "reply", response.getReply() == null ? "" : response.getReply()
                )
        );
    }
}
