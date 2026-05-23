package com.yipeng.recorder.service.agent;

import com.yipeng.recorder.model.User;
import com.yipeng.recorder.service.RelatedRecordContextService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchRecordsAgentToolTests {

    @Test
    void defaultsToAgentChunkLimitOfTen() {
        RelatedRecordContextService relatedRecordContextService = mock(RelatedRecordContextService.class);
        SearchRecordsAgentTool tool = new SearchRecordsAgentTool(relatedRecordContextService);
        User user = new User();
        when(relatedRecordContextService.getAgentRelatedChunkContexts("Amazon news", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT))
                .thenReturn(java.util.List.of());

        tool.execute(Map.of("query", "Amazon news"), user);

        verify(relatedRecordContextService)
                .getAgentRelatedChunkContexts("Amazon news", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
    }

    @Test
    void clampsRequestedLimitToAgentChunkLimitOfTen() {
        RelatedRecordContextService relatedRecordContextService = mock(RelatedRecordContextService.class);
        SearchRecordsAgentTool tool = new SearchRecordsAgentTool(relatedRecordContextService);
        User user = new User();

        tool.execute(Map.of("query", "Amazon news", "limit", 50), user);

        verify(relatedRecordContextService)
                .getAgentRelatedChunkContexts(eq("Amazon news"), eq(user), eq(RelatedRecordContextService.AGENT_CHUNK_LIMIT));
    }

    @Test
    void describesLimitAsTen() {
        SearchRecordsAgentTool tool = new SearchRecordsAgentTool(mock(RelatedRecordContextService.class));

        assertTrue(tool.getArgumentSchema().contains("1 to 10"));
        assertTrue(tool.getArgumentSchema().contains("defaults to 10"));
    }
}
