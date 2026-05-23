package com.yipeng.recorder.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.request.LlmChatMessage;
import com.yipeng.recorder.request.LlmChatRequest;
import com.yipeng.recorder.response.LlmChatResponse;
import com.yipeng.recorder.service.MarketPulseApiService;
import org.junit.jupiter.api.Test;

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
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class))).thenReturn(new LlmChatResponse("{\"final\":\"done\"}"));

        LlmChatResponse response = service.chatWithTools(request("hello"), new User());

        assertEquals("done", response.getReply());
        verify(tool, never()).execute(any(), any());
        verify(marketPulseApiService, times(1)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void proseAnswerIsTreatedAsFinalAnswer() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class))).thenReturn(new LlmChatResponse("plain final answer"));

        LlmChatResponse response = service.chatWithTools(request("hello"), new User());

        assertEquals("plain final answer", response.getReply());
        verify(tool, never()).execute(any(), any());
    }

    @Test
    void placeholderFinalAnswerRetriesInsteadOfReturningPlaceholder() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"final\":\"answer text\"}"))
                .thenReturn(new LlmChatResponse("{\"final\":\"No matching records were found.\"}"));

        LlmChatResponse response = service.chatWithTools(request("untracked stock"), new User());

        assertEquals("No matching records were found.", response.getReply());
        verify(tool, never()).execute(any(), any());
        verify(marketPulseApiService, times(2)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void emptyJsonObjectsBeforeFinalAreIgnored() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("""
                        {}
                        {}
                        {"final":"The useful answer is here."}
                        """));

        LlmChatResponse response = service.chatWithTools(request("hello"), new User());

        assertEquals("The useful answer is here.", response.getReply());
        verify(tool, never()).execute(any(), any());
        verify(marketPulseApiService, times(1)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void bareEmptyJsonRetriesInsteadOfReturningBraces() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{}"))
                .thenReturn(new LlmChatResponse("{\"final\":\"Recovered from empty JSON.\"}"));

        LlmChatResponse response = service.chatWithTools(request("hello"), new User());

        assertEquals("Recovered from empty JSON.", response.getReply());
        verify(tool, never()).execute(any(), any());
        verify(marketPulseApiService, times(2)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void emptyReplyRetriesAndFallsBackToUsefulMessage() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class))).thenReturn(new LlmChatResponse(""));

        LlmChatResponse response = service.chatWithTools(request("unknown market"), new User());

        assertTrue(response.getReply().contains("could not produce"));
        verify(tool, never()).execute(any(), any());
    }

    @Test
    void searchRecordsToolRequestExecutesRegisteredToolAndLoopsToFinalAnswer() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"tax notes\",\"limit\":3}}"))
                .thenReturn(new LlmChatResponse("{\"final\":\"I found tax notes.\"}"));
        when(tool.execute(any(), any())).thenReturn(LlmAgentToolResult.success("search_records", "TOOL_RESULT search_records\nFound notes."));

        LlmChatResponse response = service.chatWithTools(request("find tax notes"), new User());

        assertEquals("I found tax notes.", response.getReply());
        verify(tool, times(1)).execute(any(), any());
        verify(marketPulseApiService, times(2)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void toolCallTokenWrappedJsonExecutesRegisteredTool() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("<tool_call|>{\"name\":\"search_records\",\"arguments\":{\"query\":\"NVDA\",\"limit\":2}}<|/tool_call|>"))
                .thenReturn(new LlmChatResponse("{\"final\":\"NVDA context found.\"}"));
        when(tool.execute(any(), any())).thenReturn(LlmAgentToolResult.success("search_records", "TOOL_RESULT search_records\nFound NVDA."));

        LlmChatResponse response = service.chatWithTools(request("NVDA"), new User());

        assertEquals("NVDA context found.", response.getReply());
        verify(tool, times(1)).execute(any(), any());
        verify(marketPulseApiService, times(2)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void openAiStyleToolCallsShapeExecutesRegisteredTool() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("""
                        {"tool_calls":[{"function":{"name":"search_records","arguments":"{\\"query\\":\\"portfolio\\",\\"limit\\":1}"}}]}
                        """))
                .thenReturn(new LlmChatResponse("{\"final\":\"Portfolio context found.\"}"));
        when(tool.execute(any(), any())).thenReturn(LlmAgentToolResult.success("search_records", "TOOL_RESULT search_records\nFound portfolio."));

        LlmChatResponse response = service.chatWithTools(request("portfolio"), new User());

        assertEquals("Portfolio context found.", response.getReply());
        verify(tool, times(1)).execute(any(), any());
    }

    @Test
    void bareToolCallTokenRetriesInsteadOfReturningMarkerToUi() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("<tool_call|>"))
                .thenReturn(new LlmChatResponse("{\"final\":\"Recovered answer.\"}"));

        LlmChatResponse response = service.chatWithTools(request("recover"), new User());

        assertEquals("Recovered answer.", response.getReply());
        verify(tool, never()).execute(any(), any());
        verify(marketPulseApiService, times(2)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void unknownToolReturnsControlledErrorAndDoesNotExecuteRegisteredTools() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"delete_everything\",\"arguments\":{}}"))
                .thenReturn(new LlmChatResponse("{\"final\":\"I cannot use that tool.\"}"));

        LlmChatResponse response = service.chatWithTools(request("use bad tool"), new User());

        assertEquals("I cannot use that tool.", response.getReply());
        verify(tool, never()).execute(any(), any());
        verify(marketPulseApiService, times(2)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void maxToolIterationsStopsLoopAndRequestsFinalAnswer() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"one\"}}"))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"two\"}}"))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"three\"}}"))
                .thenReturn(new LlmChatResponse("{\"final\":\"Final after limit.\"}"));
        when(tool.execute(any(), any())).thenReturn(LlmAgentToolResult.success("search_records", "TOOL_RESULT search_records\nFound."));

        LlmChatResponse response = service.chatWithTools(request("loop"), new User());

        assertEquals("Final after limit.", response.getReply());
        verify(tool, times(3)).execute(any(), any());
        verify(marketPulseApiService, times(4)).chatWithLlm(any(), any(Duration.class));
    }

    @Test
    void toolFailureReturnsControlledErrorAndContinues() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"notes\"}}"))
                .thenReturn(new LlmChatResponse("{\"final\":\"I could not search records, but here is an answer.\"}"));
        when(tool.execute(any(), any())).thenThrow(new RuntimeException("Qdrant unavailable"));

        LlmChatResponse response = service.chatWithTools(request("notes"), new User());

        assertTrue(response.getReply().contains("could not search"));
        verify(tool, times(1)).execute(any(), any());
    }

    @Test
    void noRecordToolResultCanStillReturnFinalAnswer() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        LlmAgentTool tool = mockTool();
        LlmAgentService service = new LlmAgentService(
                marketPulseApiService,
                new LlmAgentToolRegistry(List.of(tool)),
                new ObjectMapper()
        );
        when(marketPulseApiService.chatWithLlm(any(), any(Duration.class)))
                .thenReturn(new LlmChatResponse("{\"tool\":\"search_records\",\"arguments\":{\"query\":\"UNTRACKED\",\"limit\":5}}"))
                .thenReturn(new LlmChatResponse(""))
                .thenReturn(new LlmChatResponse("{\"final\":\"No Recorder records matched UNTRACKED.\"}"));
        when(tool.execute(any(), any())).thenReturn(LlmAgentToolResult.success(
                "search_records",
                "TOOL_RESULT search_records\nQuery: UNTRACKED\nNo related records were found."
        ));

        LlmChatResponse response = service.chatWithTools(request("UNTRACKED"), new User());

        assertEquals("No Recorder records matched UNTRACKED.", response.getReply());
        verify(tool, times(1)).execute(any(), any());
        verify(marketPulseApiService, times(3)).chatWithLlm(any(), any(Duration.class));
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
                .thenReturn(LlmAgentToolResult.success("search_records", "TOOL_RESULT search_records\nFound."));
        return tool;
    }
}
