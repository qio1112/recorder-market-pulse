package com.yipeng.recorder.controller;

import com.yipeng.recorder.exception.ForbiddenException;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.request.LlmChatMessage;
import com.yipeng.recorder.request.LlmChatRequest;
import com.yipeng.recorder.response.LlmChatResponse;
import com.yipeng.recorder.service.LlmRecordService;
import com.yipeng.recorder.service.MarketPulseApiService;
import com.yipeng.recorder.service.UserService;
import com.yipeng.recorder.service.agent.LlmAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmControllerTests {

    @Test
    void nonAdminUserIsForbidden() {
        UserService userService = mock(UserService.class);
        LlmController controller = newController(userService, mock(MarketPulseApiService.class), mock(LlmRecordService.class), mock(LlmAgentService.class));
        when(userService.findUserFromAuthentication()).thenReturn(new User());

        assertThrows(ForbiddenException.class, () -> controller.chat(request("RELATED_CONTEXT")));
    }

    @Test
    void relatedContextModeUsesEagerEnrichmentPath() {
        UserService userService = mock(UserService.class);
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmRecordService llmRecordService = mock(LlmRecordService.class);
        LlmAgentService llmAgentService = mock(LlmAgentService.class);
        User admin = adminUser();
        LlmChatRequest request = request("RELATED_CONTEXT");
        LlmChatRequest enriched = request("DIRECT");
        when(userService.findUserFromAuthentication()).thenReturn(admin);
        when(llmRecordService.enrichChatWithRelatedChunks(eq(request), eq(admin))).thenReturn(enriched);
        when(marketPulseApiService.chatWithLlm(enriched)).thenReturn(new LlmChatResponse("old path"));

        ResponseEntity<LlmChatResponse> response = newController(userService, marketPulseApiService, llmRecordService, llmAgentService).chat(request);

        assertEquals("old path", response.getBody().getReply());
        verify(llmRecordService).enrichChatWithRelatedChunks(eq(request), eq(admin));
        verify(llmAgentService, never()).chatWithTools(any(), any());
    }

    @Test
    void recordAgentModeUsesAgentPath() {
        UserService userService = mock(UserService.class);
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmRecordService llmRecordService = mock(LlmRecordService.class);
        LlmAgentService llmAgentService = mock(LlmAgentService.class);
        User admin = adminUser();
        LlmChatRequest request = request("RECORD_AGENT");
        when(userService.findUserFromAuthentication()).thenReturn(admin);
        when(llmAgentService.chatWithTools(request, admin)).thenReturn(new LlmChatResponse("agent path"));

        ResponseEntity<LlmChatResponse> response = newController(userService, marketPulseApiService, llmRecordService, llmAgentService).chat(request);

        assertEquals("agent path", response.getBody().getReply());
        verify(llmAgentService).chatWithTools(request, admin);
        verify(llmRecordService, never()).enrichChatWithRelatedChunks(any(), any());
        verify(marketPulseApiService, never()).chatWithLlm(any());
    }

    private LlmController newController(UserService userService,
                                        MarketPulseApiService marketPulseApiService,
                                        LlmRecordService llmRecordService,
                                        LlmAgentService llmAgentService) {
        return new LlmController(userService, marketPulseApiService, llmRecordService, llmAgentService);
    }

    private LlmChatRequest request(String chatMode) {
        LlmChatRequest request = new LlmChatRequest();
        request.setChatMode(chatMode);
        request.setMessages(List.of(new LlmChatMessage("user", "hello")));
        return request;
    }

    private User adminUser() {
        User user = mock(User.class);
        when(user.isAdmin()).thenReturn(true);
        return user;
    }
}
