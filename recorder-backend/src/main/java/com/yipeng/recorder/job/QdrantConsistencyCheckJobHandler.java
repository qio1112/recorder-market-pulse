package com.yipeng.recorder.job;

import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.repository.JobExecutionRepository;
import com.yipeng.recorder.repository.RecordRepository;
import com.yipeng.recorder.service.QdrantEmbeddingService;
import com.yipeng.recorder.utils.JobStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QdrantConsistencyCheckJobHandler implements JobHandler {

    private final RecordRepository recordRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final QdrantEmbeddingService qdrantEmbeddingService;

    public QdrantConsistencyCheckJobHandler(RecordRepository recordRepository,
                                            JobExecutionRepository jobExecutionRepository,
                                            QdrantEmbeddingService qdrantEmbeddingService) {
        this.recordRepository = recordRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.qdrantEmbeddingService = qdrantEmbeddingService;
    }

    @Override
    public String jobType() {
        return JobType.QDRANT_CONSISTENCY_CHECK;
    }

    @Override
    public JobResult run(JobContext context) {
        List<Long> recordIds = recordRepository.findAllRecordIds();
        int checkedCount = 0;
        int missingCount = 0;
        int failedCheckCount = 0;

        for (Long recordId : recordIds) {
            try {
                checkedCount++;
                if (!qdrantEmbeddingService.recordExists(recordId.toString())) {
                    missingCount++;
                }
            } catch (Exception e) {
                failedCheckCount++;
            }
        }

        long failedQdrantJobCount = jobExecutionRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .filter(execution -> JobStatus.FAILED.equals(execution.getStatus()))
                .filter(execution -> JobType.QDRANT_RECORD_UPSERT.equals(execution.getJobType())
                        || JobType.QDRANT_RECORD_DELETE.equals(execution.getJobType()))
                .count();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("checkedAt", java.time.ZonedDateTime.now().toString());
        details.put("recordCount", recordIds.size());
        details.put("checkedCount", checkedCount);
        details.put("missingCount", missingCount);
        details.put("staleCount", 0);
        details.put("failedCheckCount", failedCheckCount);
        details.put("failedUpsertDeleteCount", failedQdrantJobCount);
        return JobResult.of("Checked Qdrant consistency: %d records, %d missing, %d failed checks.".formatted(
                recordIds.size(),
                missingCount,
                failedCheckCount
        ), details);
    }
}
