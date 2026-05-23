package com.yipeng.recorder.job;

import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.repository.RecordRepository;
import com.yipeng.recorder.service.QdrantEmbeddingService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QdrantDatafixJobHandlerTests {

    @Test
    void upsertsRecordsMissingFromQdrantOnly() {
        RecordRepository recordRepository = mock(RecordRepository.class);
        QdrantEmbeddingService qdrantEmbeddingService = mock(QdrantEmbeddingService.class);
        QdrantDatafixJobHandler handler = new QdrantDatafixJobHandler(recordRepository, qdrantEmbeddingService);
        Record existing = record(1L);
        Record missing = record(2L);
        when(recordRepository.findAll()).thenReturn(List.of(existing, missing));
        when(qdrantEmbeddingService.listRecordIds()).thenReturn(Set.of("1"));
        when(qdrantEmbeddingService.upsertRecordSync(missing, missing.getCreatedBy())).thenReturn(List.of("point-2"));

        JobResult result = handler.run(null);

        assertEquals("Qdrant datafix checked 2 records, found 1 missing and 0 stale, upserted 1, deleted stale 0, failed upserts 0, failed deletes 0.", result.summary());
        assertEquals(2, result.details().get("recordCount"));
        assertEquals(1, result.details().get("missingCount"));
        assertEquals(0, result.details().get("staleCount"));
        assertEquals(1, result.details().get("upsertedCount"));
        verify(qdrantEmbeddingService, never()).upsertRecordSync(existing, existing.getCreatedBy());
        verify(qdrantEmbeddingService).upsertRecordSync(missing, missing.getCreatedBy());
    }

    @Test
    void upsertsMissingRecordsAndDeletesStaleRecords() {
        RecordRepository recordRepository = mock(RecordRepository.class);
        QdrantEmbeddingService qdrantEmbeddingService = mock(QdrantEmbeddingService.class);
        QdrantDatafixJobHandler handler = new QdrantDatafixJobHandler(recordRepository, qdrantEmbeddingService);
        Record existing = record(1L);
        Record missing = record(2L);
        when(recordRepository.findAll()).thenReturn(List.of(existing, missing));
        when(qdrantEmbeddingService.listRecordIds()).thenReturn(Set.of("1", "99"));
        when(qdrantEmbeddingService.upsertRecordSync(missing, missing.getCreatedBy())).thenReturn(List.of("point-2"));
        when(qdrantEmbeddingService.deleteRecordIfExistsSync("99")).thenReturn(true);

        JobResult result = handler.run(null);

        assertEquals(2, result.details().get("checkedCount"));
        assertEquals(1, result.details().get("missingCount"));
        assertEquals(1, result.details().get("staleCount"));
        assertEquals(1, result.details().get("upsertedCount"));
        assertEquals(1, result.details().get("deletedStaleCount"));
        verify(qdrantEmbeddingService).upsertRecordSync(missing, missing.getCreatedBy());
        verify(qdrantEmbeddingService).deleteRecordIfExistsSync("99");
    }

    @Test
    void recordsUpsertAndDeleteFailuresWithoutAborting() {
        RecordRepository recordRepository = mock(RecordRepository.class);
        QdrantEmbeddingService qdrantEmbeddingService = mock(QdrantEmbeddingService.class);
        QdrantDatafixJobHandler handler = new QdrantDatafixJobHandler(recordRepository, qdrantEmbeddingService);
        Record upsertFailure = record(2L);
        Record success = record(3L);
        when(recordRepository.findAll()).thenReturn(List.of(upsertFailure, success));
        when(qdrantEmbeddingService.listRecordIds()).thenReturn(Set.of("99"));
        when(qdrantEmbeddingService.upsertRecordSync(upsertFailure, upsertFailure.getCreatedBy()))
                .thenThrow(new RuntimeException("upsert timeout"));
        when(qdrantEmbeddingService.upsertRecordSync(success, success.getCreatedBy())).thenReturn(List.of("point-3"));
        when(qdrantEmbeddingService.deleteRecordIfExistsSync("99")).thenThrow(new RuntimeException("delete timeout"));

        JobResult result = handler.run(null);

        assertEquals(2, result.details().get("checkedCount"));
        assertEquals(2, result.details().get("missingCount"));
        assertEquals(1, result.details().get("staleCount"));
        assertEquals(1, result.details().get("upsertedCount"));
        assertEquals(1, result.details().get("failedUpsertCount"));
        assertEquals(1, result.details().get("failedDeleteCount"));
        Map<?, ?> failures = (Map<?, ?>) result.details().get("failuresFirst100");
        assertEquals(2, failures.size());
    }

    private Record record(Long id) {
        User user = new User();
        user.setUsername("admin");
        Record record = new Record("title " + id, user, "content", false);
        record.setId(id);
        return record;
    }
}
