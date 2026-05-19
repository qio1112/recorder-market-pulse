package com.yipeng.recorder.controller;

import com.yipeng.recorder.exception.ForbiddenException;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.request.GenerateRecordLabelsRequest;
import com.yipeng.recorder.request.LlmChatRequest;
import com.yipeng.recorder.request.SaveLlmChatRecordRequest;
import com.yipeng.recorder.response.GenerateRecordLabelsResponse;
import com.yipeng.recorder.response.LlmChatResponse;
import com.yipeng.recorder.response.SaveLlmChatRecordResponse;
import com.yipeng.recorder.service.LlmRecordService;
import com.yipeng.recorder.service.MarketPulseApiService;
import com.yipeng.recorder.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final UserService userService;
    private final MarketPulseApiService marketPulseApiService;
    private final LlmRecordService llmRecordService;

    public LlmController(UserService userService,
                         MarketPulseApiService marketPulseApiService,
                         LlmRecordService llmRecordService) {
        this.userService = userService;
        this.marketPulseApiService = marketPulseApiService;
        this.llmRecordService = llmRecordService;
    }

    @PostMapping("/chat")
    public ResponseEntity<LlmChatResponse> chat(@RequestBody LlmChatRequest request) {
        User user = userService.findUserFromAuthentication();
        if (user == null || !user.isAdmin()) {
            throw new ForbiddenException();
        }
        LlmChatRequest enrichedRequest = Boolean.TRUE.equals(request.getIncludeRelatedRecords())
                ? llmRecordService.enrichChatWithRelatedChunks(request, user)
                : request;
        return ResponseEntity.ok(marketPulseApiService.chatWithLlm(enrichedRequest));
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
