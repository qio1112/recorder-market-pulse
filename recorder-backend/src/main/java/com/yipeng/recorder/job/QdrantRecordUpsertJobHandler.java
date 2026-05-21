package com.yipeng.recorder.job;

import com.yipeng.recorder.exception.InvalidRequestException;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.repository.RecordRepository;
import com.yipeng.recorder.service.QdrantEmbeddingService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QdrantRecordUpsertJobHandler implements JobHandler {

    private final RecordRepository recordRepository;
    private final QdrantEmbeddingService qdrantEmbeddingService;

    public QdrantRecordUpsertJobHandler(RecordRepository recordRepository,
                                        QdrantEmbeddingService qdrantEmbeddingService) {
        this.recordRepository = recordRepository;
        this.qdrantEmbeddingService = qdrantEmbeddingService;
    }

    @Override
    public String jobType() {
        return JobType.QDRANT_RECORD_UPSERT;
    }

    @Override
    public JobResult run(JobContext context) {
        Long recordId = getRecordId(context.parameters());
        Record record = recordRepository.findById(recordId)
                .orElseThrow(() -> new InvalidRequestException("Record not found for Qdrant upsert: " + recordId));
        List<String> pointIds = qdrantEmbeddingService.upsertRecordSync(record, record.getCreatedBy());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("recordId", recordId);
        details.put("pointCount", pointIds.size());
        return JobResult.of("Upserted record %d into Qdrant with %d point(s).".formatted(recordId, pointIds.size()), details);
    }

    private Long getRecordId(Map<String, Object> parameters) {
        Object value = parameters.get("recordId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        throw new InvalidRequestException("recordId is required for Qdrant upsert.");
    }
}
