package com.yipeng.recorder.job;

import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.repository.RecordRepository;
import com.yipeng.recorder.service.QdrantEmbeddingService;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class QdrantDatafixJobHandler implements JobHandler {

    private static final int DETAIL_ID_LIMIT = 100;

    private final RecordRepository recordRepository;
    private final QdrantEmbeddingService qdrantEmbeddingService;

    public QdrantDatafixJobHandler(RecordRepository recordRepository,
                                   QdrantEmbeddingService qdrantEmbeddingService) {
        this.recordRepository = recordRepository;
        this.qdrantEmbeddingService = qdrantEmbeddingService;
    }

    @Override
    public String jobType() {
        return JobType.QDRANT_DATAFIX;
    }

    @Override
    public JobResult run(JobContext context) {
        List<Record> records = recordRepository.findAll();
        int checkedCount = 0;
        int missingCount = 0;
        int staleCount = 0;
        int upsertedCount = 0;
        int deletedStaleCount = 0;
        int failedUpsertCount = 0;
        int failedDeleteCount = 0;
        List<Long> missingRecordIds = new ArrayList<>();
        List<Long> upsertedRecordIds = new ArrayList<>();
        List<String> staleRecordIds = new ArrayList<>();
        List<String> deletedStaleRecordIds = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        Set<String> backendRecordIds = records.stream()
                .map(Record::getId)
                .filter(id -> id != null)
                .map(String::valueOf)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> qdrantRecordIds = qdrantEmbeddingService.listRecordIds();

        for (Record record : records) {
            if (record.getId() == null) {
                continue;
            }
            checkedCount++;
            if (qdrantRecordIds.contains(record.getId().toString())) {
                continue;
            }
            missingCount++;
            addLimited(missingRecordIds, record.getId());
            try {
                qdrantEmbeddingService.upsertRecordSync(record, record.getCreatedBy());
                upsertedCount++;
                addLimited(upsertedRecordIds, record.getId());
            } catch (Exception e) {
                failedUpsertCount++;
                putFailure(failures, record.getId(), "upsert failed: " + e.getMessage());
            }
        }

        for (String qdrantRecordId : qdrantRecordIds) {
            if (backendRecordIds.contains(qdrantRecordId)) {
                continue;
            }
            staleCount++;
            addLimited(staleRecordIds, qdrantRecordId);
            try {
                qdrantEmbeddingService.deleteRecordIfExistsSync(qdrantRecordId);
                deletedStaleCount++;
                addLimited(deletedStaleRecordIds, qdrantRecordId);
            } catch (Exception e) {
                failedDeleteCount++;
                putFailure(failures, qdrantRecordId, "stale delete failed: " + e.getMessage());
            }
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fixedAt", ZonedDateTime.now().toString());
        details.put("recordCount", records.size());
        details.put("checkedCount", checkedCount);
        details.put("missingCount", missingCount);
        details.put("staleCount", staleCount);
        details.put("upsertedCount", upsertedCount);
        details.put("deletedStaleCount", deletedStaleCount);
        details.put("failedUpsertCount", failedUpsertCount);
        details.put("failedDeleteCount", failedDeleteCount);
        details.put("missingRecordIdsFirst100", missingRecordIds);
        details.put("upsertedRecordIdsFirst100", upsertedRecordIds);
        details.put("staleRecordIdsFirst100", staleRecordIds);
        details.put("deletedStaleRecordIdsFirst100", deletedStaleRecordIds);
        details.put("failuresFirst100", failures);

        String summary = "Qdrant datafix checked %d records, found %d missing and %d stale, upserted %d, deleted stale %d, failed upserts %d, failed deletes %d."
                .formatted(checkedCount, missingCount, staleCount, upsertedCount, deletedStaleCount, failedUpsertCount, failedDeleteCount);
        return JobResult.of(summary, details);
    }

    private void addLimited(List<Long> values, Long id) {
        if (values.size() < DETAIL_ID_LIMIT) {
            values.add(id);
        }
    }

    private void addLimited(List<String> values, String id) {
        if (values.size() < DETAIL_ID_LIMIT) {
            values.add(id);
        }
    }

    private void putFailure(Map<String, String> failures, Long id, String message) {
        putFailure(failures, String.valueOf(id), message);
    }

    private void putFailure(Map<String, String> failures, String id, String message) {
        if (failures.size() < DETAIL_ID_LIMIT) {
            failures.put(id, message);
        }
    }
}
