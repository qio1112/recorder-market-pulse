package com.yipeng.recorder.job;

import com.yipeng.recorder.exception.InvalidRequestException;
import com.yipeng.recorder.service.QdrantEmbeddingService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class QdrantRecordDeleteJobHandler implements JobHandler {

    private final QdrantEmbeddingService qdrantEmbeddingService;

    public QdrantRecordDeleteJobHandler(QdrantEmbeddingService qdrantEmbeddingService) {
        this.qdrantEmbeddingService = qdrantEmbeddingService;
    }

    @Override
    public String jobType() {
        return JobType.QDRANT_RECORD_DELETE;
    }

    @Override
    public JobResult run(JobContext context) {
        Long recordId = getRecordId(context.parameters());
        boolean existed = qdrantEmbeddingService.deleteRecordIfExistsSync(recordId.toString());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("recordId", recordId);
        details.put("existed", existed);
        return JobResult.of("Deleted Qdrant vectors for record %d if present.".formatted(recordId), details);
    }

    private Long getRecordId(Map<String, Object> parameters) {
        Object value = parameters.get("recordId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        throw new InvalidRequestException("recordId is required for Qdrant delete.");
    }
}
