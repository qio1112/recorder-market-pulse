package com.yipeng.recorder.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.prompt.BuiltInLlmTokenLimits;
import com.yipeng.recorder.request.LlmChatMessage;
import com.yipeng.recorder.request.LlmChatRequest;
import com.yipeng.recorder.response.LlmChatResponse;
import com.yipeng.recorder.service.MarketPulseApiService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmAgentServiceTests {

    @Test
    void directAnswerReturnsWithoutToolExecution() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = service(marketPulseApiService, tool);
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class))).thenReturn(new LlmChatResponse("{\"final\":\"done\"}"));

        LlmChatResponse response = service.chatWithTools(request("hello"), new User());

        assertEquals("done", response.getReply());
        verify(tool, never()).execute(any(), any());
        verify(marketPulseApiService, times(1)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void noRecordToolResultReturnsImmediatelyWithoutFinalLlmCall() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = service(marketPulseApiService, tool);
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"UNTRACKED\",\"limit\":5}}"));
        when(tool.execute(any(), any())).thenReturn(LlmAgentToolResult.success(
                "search_records",
                "TOOL_RESULT search_records\nQuery: UNTRACKED\nNo related records were found."
        ));

        LlmChatResponse response = service.chatWithTools(request("UNTRACKED"), new User());

        assertEquals("No related Recorder records were found for this question.", response.getReply());
        verify(tool, times(1)).execute(any(), any());
        verify(marketPulseApiService, times(1)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void toolFailureDoesNotClaimNoRelatedRecords() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = service(marketPulseApiService, tool);
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"notes\"}}"));
        when(tool.execute(any(), any())).thenThrow(new RuntimeException("Qdrant unavailable"));

        LlmChatResponse response = service.chatWithTools(request("notes"), new User());

        assertTrue(response.getReply().contains("could not search Recorder records"));
        verify(tool, times(1)).execute(any(), any());
        verify(marketPulseApiService, times(1)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void relatedRecordsFoundRunsOneFinalSynthesisCall() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = service(marketPulseApiService, tool);
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"tax notes\",\"limit\":3}}"))
                .thenReturn(new LlmChatResponse("Tax notes mention estimated payments [12]."));
        when(tool.execute(any(), any())).thenReturn(LlmAgentToolResult.success(
                "search_records",
                """
                        TOOL_RESULT search_records
                        Query: tax notes
                        Related records found: 1

                        Source [12]: Tax Notes | score 0.812 | modified 2026-05-01
                        Estimated payments are due quarterly.
                        """.trim()
        ));

        LlmChatResponse response = service.chatWithTools(request("find tax notes"), new User());

        assertEquals("Tax notes mention estimated payments [12].", response.getReply());
        verify(tool, times(1)).execute(any(), any());
        verify(marketPulseApiService, times(2)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void relatedRecordsFoundAcceptsMarkdownProseFinalAnswer() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = service(marketPulseApiService, tool);
        String amazonAnswer = """
                Recent news regarding Amazon includes the following:

                * **Stock Performance:** As of late May 2026, Amazon's stock has risen approximately 16.31% year-to-date, driven by strong sales and first-quarter earnings [15].
                * **AI and Partnerships:** The company's growth is being fueled by its leadership in AI and its partnership with Anthropic [15].
                * **Cybersecurity Initiatives:** Under "Project Glasswing," the Mythos cybersecurity model is being rolled out to select tech giants, including Amazon.
                """.trim();
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"Amazon news\"}}"))
                .thenReturn(new LlmChatResponse(amazonAnswer));
        when(tool.execute(any(), any())).thenReturn(LlmAgentToolResult.success(
                "search_records",
                """
                        TOOL_RESULT search_records
                        Query: Amazon news
                        Related records found: 1

                        Source [15]: Amazon Stock and AI News | score 0.901 | modified 2026-05-20
                        Amazon stock rose and AI partnerships were noted.
                        """.trim()
        ));

        LlmChatResponse response = service.chatWithTools(request("Any news about Amazon?"), new User());

        assertEquals(amazonAnswer, response.getReply());
        verify(tool, times(1)).execute(any(), any());
        verify(marketPulseApiService, times(2)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void relatedRecordsFoundButEmptyFinalOutputReturnsTruthfulFallback() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = service(marketPulseApiService, tool);
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"NVDA\"}}"))
                .thenReturn(new LlmChatResponse(""));
        when(tool.execute(any(), any())).thenReturn(LlmAgentToolResult.success(
                "search_records",
                """
                        TOOL_RESULT search_records
                        Query: NVDA
                        Related records found: 1

                        Source [31]: NVDA Earnings | score 0.901 | modified 2026-05-20
                        Revenue growth remained strong.
                        """.trim()
        ));

        LlmChatResponse response = service.chatWithTools(request("NVDA"), new User());

        assertTrue(response.getReply().contains("I found related Recorder records"));
        verify(tool, times(1)).execute(any(), any());
        verify(marketPulseApiService, times(2)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void relatedRecordsFoundButSecondToolRequestDoesNotExecuteAnotherSearch() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = service(marketPulseApiService, tool);
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"portfolio\"}}"))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"portfolio again\"}}"));
        when(tool.execute(any(), any())).thenReturn(LlmAgentToolResult.success(
                "search_records",
                """
                        TOOL_RESULT search_records
                        Query: portfolio
                        Related records found: 1

                        Source [44]: Portfolio Notes | score 0.778 | modified 2026-05-21
                        Portfolio allocation shifted toward cash.
                        """.trim()
        ));

        LlmChatResponse response = service.chatWithTools(request("portfolio"), new User());

        assertTrue(response.getReply().contains("I found related Recorder records"));
        verify(tool, times(1)).execute(any(), any());
        verify(marketPulseApiService, times(2)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void relatedRecordsFoundButLowSignalFinalOutputReturnsTruthfulFallback() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = service(marketPulseApiService, tool);
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"notes\"}}"))
                .thenReturn(new LlmChatResponse("xqzvbnmpqwrtyplkjhgfdsazxcvbnmqwertyplkjhgfdsazxcvbnmqwerty"));
        when(tool.execute(any(), any())).thenReturn(LlmAgentToolResult.success(
                "search_records",
                """
                        TOOL_RESULT search_records
                        Query: notes
                        Related records found: 1

                        Source [5]: Notes | score 0.700 | modified 2026-05-10
                        Useful context.
                        """.trim()
        ));

        LlmChatResponse response = service.chatWithTools(request("notes"), new User());

        assertTrue(response.getReply().contains("I found related Recorder records"));
    }

    @Test
    void agentModeAppliesBackendMaxTokenCap() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = service(marketPulseApiService, tool);
        LlmChatRequest request = request("hello");
        request.setMaxTokens(5000);
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class))).thenReturn(new LlmChatResponse("{\"final\":\"done\"}"));

        service.chatWithTools(request, new User());

        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(marketPulseApiService).chatWithLlm(captor.capture(), any(Duration.class));
        assertEquals(BuiltInLlmTokenLimits.RECORD_AGENT_CHAT_MAX_TOKENS, captor.getValue().getMaxTokens());
    }

    private LlmAgentService service(MarketPulseApiService marketPulseApiService, LlmAgentTool tool) {
        return new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
    }

    private LlmChatRequest request(String content) {
        LlmChatRequest request = new LlmChatRequest();
        request.setMessages(List.of(new LlmChatMessage("user", content)));
        return request;
    }

    private LlmAgentTool mockTool() {
        LlmAgentTool tool = mock(LlmAgentTool.class);
        when(tool.getName()).thenReturn("search_records");
        when(tool.getDescription()).thenReturn("Search records");
        when(tool.getArgumentSchema()).thenReturn("{\"query\":\"string\"}");
        when(tool.execute(any(Map.class), any(User.class)))
                .thenReturn(LlmAgentToolResult.success(
                        "search_records",
                        "TOOL_RESULT search_records\nQuery: notes\nRelated records found: 1\n\nSource [1]: Notes | score 0.700 | modified 2026-05-01\nFound."
                ));
        return tool;
    }
}
