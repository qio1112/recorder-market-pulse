package com.yipeng.recorder.service;

import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.response.QdrantQueryResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelatedRecordContextServiceTests {

    @Test
    void agentRetrievalFetchesDoubleCandidatesAndSortsByMostRecentlyModifiedBeforeLimit() {
        QdrantEmbeddingService qdrantEmbeddingService = mock(QdrantEmbeddingService.class);
        RecordService recordService = mock(RecordService.class);
        RelatedRecordContextService service = new RelatedRecordContextService(qdrantEmbeddingService, recordService);
        User user = adminUser();

        when(qdrantEmbeddingService.querySimilarRecords("Amazon", user, RelatedRecordContextService.DEFAULT_THRESHOLD, 20))
                .thenReturn(List.of(
                        qdrantResult("1", 0.99, "older high score"),
                        qdrantResult("2", 0.80, "newer lower score"),
                        qdrantResult("3", 0.90, "middle score")
                ));
        Record older = record(1L, "Older", "2026-05-01T10:00:00-04:00");
        Record newer = record(2L, "Newer", "2026-05-03T10:00:00-04:00");
        Record middle = record(3L, "Middle", "2026-05-02T10:00:00-04:00");
        when(recordService.getRecordById(1L)).thenReturn(older);
        when(recordService.getRecordById(2L)).thenReturn(newer);
        when(recordService.getRecordById(3L)).thenReturn(middle);

        List<RelatedRecordContextService.RelatedChunkContext> chunks =
                service.getAgentRelatedChunkContexts("Amazon", user, 10);

        verify(qdrantEmbeddingService).querySimilarRecords("Amazon", user, RelatedRecordContextService.DEFAULT_THRESHOLD, 20);
        assertEquals(List.of(2L, 3L, 1L), chunks.stream().map(RelatedRecordContextService.RelatedChunkContext::recordId).toList());
    }

    @Test
    void contextUsesRecordIdCitationsInsteadOfExcerptIndexes() {
        RelatedRecordContextService service = new RelatedRecordContextService(
                mock(QdrantEmbeddingService.class),
                mock(RecordService.class)
        );
        List<RelatedRecordContextService.RelatedChunkContext> chunks = List.of(
                new RelatedRecordContextService.RelatedChunkContext(
                        12L,
                        "Long Research Note",
                        ZonedDateTime.parse("2026-05-01T10:00:00-04:00"),
                        ZonedDateTime.parse("2026-05-02T10:00:00-04:00"),
                        0.91d,
                        false,
                        "Important detail."
                )
        );

        String relatedPrompt = service.buildRelatedChunksPrompt(chunks);
        String toolContent = service.buildCompactToolResultContent("research", chunks);

        assertTrue(relatedPrompt.contains("Source [12]: Long Research Note"));
        assertTrue(toolContent.contains("Source [12]: Long Research Note"));
        assertFalse(relatedPrompt.contains("[1] Source:"));
        assertFalse(toolContent.contains("[1] Long Research Note"));
    }

    private QdrantQueryResult qdrantResult(String recordId, double score, String chunk) {
        QdrantQueryResult result = new QdrantQueryResult();
        ReflectionTestUtils.setField(result, "recordId", recordId);
        ReflectionTestUtils.setField(result, "bestScore", score);
        ReflectionTestUtils.setField(result, "chunks", List.of(chunk));
        return result;
    }

    private Record record(Long id, String title, String modifiedAt) {
        User owner = adminUser();
        Record record = new Record(title, owner, "content", false);
        record.setId(id);
        record.setCreationTime(ZonedDateTime.parse(modifiedAt));
        record.setLastModifiedTime(ZonedDateTime.parse(modifiedAt));
        return record;
    }

    private User adminUser() {
        User user = mock(User.class);
        when(user.isAdmin()).thenReturn(true);
        return user;
    }
}
