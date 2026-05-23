package com.yipeng.recorder.job;

import com.yipeng.recorder.repository.JobExecutionRepository;
import com.yipeng.recorder.repository.RecordRepository;
import com.yipeng.recorder.service.QdrantEmbeddingService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QdrantConsistencyCheckJobHandlerTests {

    @Test
    void reportsMissingAndStaleRecordEmbeddings() {
        RecordRepository recordRepository = mock(RecordRepository.class);
        JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
        QdrantEmbeddingService qdrantEmbeddingService = mock(QdrantEmbeddingService.class);
        QdrantConsistencyCheckJobHandler handler = new QdrantConsistencyCheckJobHandler(
                recordRepository,
                jobExecutionRepository,
                qdrantEmbeddingService
        );
        when(recordRepository.findAllRecordIds()).thenReturn(List.of(1L, 2L));
        when(qdrantEmbeddingService.listRecordIds()).thenReturn(Set.of("1", "99"));
        when(jobExecutionRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of());

        JobResult result = handler.run(null);

        assertEquals("Checked Qdrant consistency: 2 records, 1 missing, 1 stale, 0 failed checks.", result.summary());
        assertEquals(1, result.details().get("missingCount"));
        assertEquals(1, result.details().get("staleCount"));
        assertEquals(0, result.details().get("failedCheckCount"));
    }
}
