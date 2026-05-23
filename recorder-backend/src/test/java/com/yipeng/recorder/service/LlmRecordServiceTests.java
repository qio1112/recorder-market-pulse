package com.yipeng.recorder.service;

import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.request.LlmChatMessage;
import com.yipeng.recorder.response.LlmChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmRecordServiceTests {

    @Test
    void createChatRecordStoresSummaryWithoutTranscript() {
        MarketPulseApiService marketPulseApiService = mock(MarketPulseApiService.class);
        RecordService recordService = mock(RecordService.class);
        QdrantJobService qdrantJobService = mock(QdrantJobService.class);
        RelatedRecordContextService relatedRecordContextService = mock(RelatedRecordContextService.class);
        LlmRecordService service = new LlmRecordService(
                marketPulseApiService,
                recordService,
                qdrantJobService,
                relatedRecordContextService
        );
        User user = new User();
        user.setUsername("admin");
        String summary = "Full useful summary.\n\nIncludes decisions and next actions.";

        when(marketPulseApiService.chatWithLlm(any()))
                .thenReturn(new LlmChatResponse(summary))
                .thenReturn(new LlmChatResponse("Useful Chat Title"))
                .thenReturn(new LlmChatResponse("[\"MARKET\", \"PLAN\"]"));
        when(recordService.createRecord(
                any(Record.class),
                eq(Collections.emptyList()),
                eq(Collections.emptyList()),
                any(),
                eq(user),
                isNull(),
                anyBoolean(),
                any()
        )).thenAnswer(invocation -> invocation.getArgument(0));

        service.createChatRecord(List.of(
                new LlmChatMessage("user", "What should I do with NVDA?"),
                new LlmChatMessage("assistant", "Here is the analysis.")
        ), false, user);

        ArgumentCaptor<Record> recordCaptor = ArgumentCaptor.forClass(Record.class);
        verify(recordService).createRecord(
                recordCaptor.capture(),
                eq(Collections.emptyList()),
                eq(Collections.emptyList()),
                any(),
                eq(user),
                isNull(),
                eq(false),
                any()
        );
        Record record = recordCaptor.getValue();
        assertEquals(summary, record.getContent());
        assertFalse(record.getContent().contains("Transcript"));
        assertFalse(record.getContent().contains("USER:"));
        assertFalse(record.getContent().contains("ASSISTANT:"));
    }
}
