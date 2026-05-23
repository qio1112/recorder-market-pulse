package com.yipeng.recorder.controller;

import com.yipeng.recorder.exception.ForbiddenException;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.prompt.BuiltInLlmTokenLimits;
import com.yipeng.recorder.request.GenerateRecordLabelsRequest;
import com.yipeng.recorder.request.LlmChatRequest;
import com.yipeng.recorder.request.SaveLlmChatRecordRequest;
import com.yipeng.recorder.response.GenerateRecordLabelsResponse;
import com.yipeng.recorder.response.LlmChatResponse;
import com.yipeng.recorder.response.SaveLlmChatRecordResponse;
import com.yipeng.recorder.service.LlmRecordService;
import com.yipeng.recorder.service.MarketPulseApiService;
import com.yipeng.recorder.service.UserService;
import com.yipeng.recorder.service.agent.LlmAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final UserService userService;
    private final MarketPulseApiService marketPulseApiService;
    private final LlmRecordService llmRecordService;
    private final LlmAgentService llmAgentService;

    public LlmController(UserService userService,
                         MarketPulseApiService marketPulseApiService,
                         LlmRecordService llmRecordService,
                         LlmAgentService llmAgentService) {
        this.userService = userService;
        this.marketPulseApiService = marketPulseApiService;
        this.llmRecordService = llmRecordService;
        this.llmAgentService = llmAgentService;
    }

    @PostMapping("/chat")
    public ResponseEntity<LlmChatResponse> chat(@RequestBody LlmChatRequest request) {
        User user = userService.findUserFromAuthentication();
        if (user == null || !user.isAdmin()) {
            throw new ForbiddenException();
        }
        if ("RECORD_AGENT".equalsIgnoreCase(normalizeChatMode(request))) {
            return ResponseEntity.ok(llmAgentService.chatWithTools(request, user));
        }
        LlmChatRequest enrichedRequest = shouldUseRelatedContext(request)
                ? llmRecordService.enrichChatWithRelatedChunks(request, user)
                : request;
        applyChatDefaults(enrichedRequest);
        return ResponseEntity.ok(marketPulseApiService.chatWithLlm(enrichedRequest));
    }

    private void applyChatDefaults(LlmChatRequest request) {
        if (request != null) {
            request.setMaxTokens(BuiltInLlmTokenLimits.CHAT_MAX_TOKENS);
        }
    }

    private String normalizeChatMode(LlmChatRequest request) {
        return request == null || request.getChatMode() == null
                ? ""
                : request.getChatMode().trim().toUpperCase(Locale.ROOT);
    }

    private boolean shouldUseRelatedContext(LlmChatRequest request) {
        String chatMode = normalizeChatMode(request);
        return "RELATED_CONTEXT".equals(chatMode) || (request != null && Boolean.TRUE.equals(request.getIncludeRelatedRecords()));
    }

    @PostMapping("/record-labels")
    public ResponseEntity<GenerateRecordLabelsResponse> generateRecordLabels(@RequestBody GenerateRecordLabelsRequest request) {
        User user = userService.findUserFromAuthentication();
        if (user == null || !user.isAdmin()) {
            throw new ForbiddenException();
        }
        List<String> labels = llmRecordService.generateLabels(
                request.getTitle(),
                request.getContent(),
                request.getMaxLabels()
        );
        return ResponseEntity.ok(new GenerateRecordLabelsResponse(labels));
    }

    @PostMapping("/chat-record")
    public ResponseEntity<SaveLlmChatRecordResponse> saveChatAsRecord(@RequestBody SaveLlmChatRecordRequest request) {
        User user = userService.findUserFromAuthentication();
        if (user == null || !user.isAdmin()) {
            throw new ForbiddenException();
        }
        llmRecordService.createChatRecordAsync(request.getMessages(), request.isPublic(), user);
        return ResponseEntity.accepted().body(new SaveLlmChatRecordResponse(
                "started",
                "LLM chat record creation started."
        ));
    }
}
