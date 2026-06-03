package com.yipeng.recorder.service.agent;

import com.yipeng.recorder.model.User;
import com.yipeng.recorder.service.RelatedRecordContextService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        assertTrue(tool.getArgumentSchema().contains("queries"));
        assertTrue(tool.getArgumentSchema().contains("1 to 5"));
    }

    @Test
    void searchesEachProvidedQueryAndMergesResults() {
        RelatedRecordContextService relatedRecordContextService = new RelatedRecordContextService(
                mock(com.yipeng.recorder.service.QdrantEmbeddingService.class),
                mock(com.yipeng.recorder.service.RecordService.class)
        );
        RelatedRecordContextService spyService = org.mockito.Mockito.spy(relatedRecordContextService);
        SearchRecordsAgentTool tool = new SearchRecordsAgentTool(spyService);
        User user = new User();

        when(spyService.getAgentRelatedChunkContexts("Amazon", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT))
                .thenReturn(List.of(chunk(12L, "Amazon News", "Amazon detail")));
        when(spyService.getAgentRelatedChunkContexts("Microsoft", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT))
                .thenReturn(List.of(chunk(20L, "Microsoft News", "Microsoft detail")));

        LlmAgentToolResult result = tool.execute(Map.of("queries", List.of("Amazon", "Microsoft")), user);

        String content = result.renderForModel();
        assertTrue(content.contains("Queries searched: Amazon; Microsoft"));
        assertTrue(content.contains("Source [12]: Amazon News"));
        assertTrue(content.contains("Source [20]: Microsoft News"));
        verify(spyService).getAgentRelatedChunkContexts("Amazon", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
        verify(spyService).getAgentRelatedChunkContexts("Microsoft", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
    }

    @Test
    void limitsSearchQueriesToFive() {
        RelatedRecordContextService relatedRecordContextService = mock(RelatedRecordContextService.class);
        SearchRecordsAgentTool tool = new SearchRecordsAgentTool(relatedRecordContextService);
        User user = new User();

        tool.execute(Map.of("queries", List.of("A", "B", "C", "D", "E", "F")), user);

        verify(relatedRecordContextService).getAgentRelatedChunkContexts("A", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
        verify(relatedRecordContextService).getAgentRelatedChunkContexts("B", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
        verify(relatedRecordContextService).getAgentRelatedChunkContexts("C", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
        verify(relatedRecordContextService).getAgentRelatedChunkContexts("D", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
        verify(relatedRecordContextService).getAgentRelatedChunkContexts("E", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
        verify(relatedRecordContextService, never()).getAgentRelatedChunkContexts("F", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
    }

    @Test
    void splitsLegacySingleQueryWhenItContainsMultipleConcepts() {
        RelatedRecordContextService relatedRecordContextService = mock(RelatedRecordContextService.class);
        SearchRecordsAgentTool tool = new SearchRecordsAgentTool(relatedRecordContextService);
        User user = new User();

        tool.execute(Map.of("query", "Amazon and Microsoft, NVDA"), user);

        verify(relatedRecordContextService).getAgentRelatedChunkContexts("Amazon", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
        verify(relatedRecordContextService).getAgentRelatedChunkContexts("Microsoft", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
        verify(relatedRecordContextService).getAgentRelatedChunkContexts("NVDA", user, RelatedRecordContextService.AGENT_CHUNK_LIMIT);
    }

    @Test
    void returnsErrorWhenNoQueryIsProvided() {
        SearchRecordsAgentTool tool = new SearchRecordsAgentTool(mock(RelatedRecordContextService.class));

        LlmAgentToolResult result = tool.execute(Map.of(), new User());

        assertEquals(false, result.isSuccess());
        assertTrue(result.renderForModel().contains("Missing required argument"));
    }

    private RelatedRecordContextService.RelatedChunkContext chunk(Long recordId, String title, String text) {
        return new RelatedRecordContextService.RelatedChunkContext(
                recordId,
                title,
                null,
                null,
                0.8d,
                false,
                text
        );
    }
}
