package com.yipeng.recorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.recorder.exception.InvalidRequestException;
import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.ScheduledJobConfig;
import com.yipeng.recorder.repository.ScheduledJobConfigRepository;
import com.yipeng.recorder.utils.JobTriggerType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Map;

@Service
public class QdrantJobService {

    public static final String UPSERT_JOB_KEY = "qdrant-record-upsert";
    public static final String DELETE_JOB_KEY = "qdrant-record-delete";

    private final ScheduledJobConfigRepository scheduledJobConfigRepository;
    private final JobExecutionService jobExecutionService;
    private final JobSchedulerService jobSchedulerService;
    private final ObjectMapper objectMapper;

    public QdrantJobService(ScheduledJobConfigRepository scheduledJobConfigRepository,
                            JobExecutionService jobExecutionService,
                            JobSchedulerService jobSchedulerService,
                            ObjectMapper objectMapper) {
        this.scheduledJobConfigRepository = scheduledJobConfigRepository;
        this.jobExecutionService = jobExecutionService;
        this.jobSchedulerService = jobSchedulerService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobExecution queueUpsert(Record record) {
        ScheduledJobConfig config = getConfig(UPSERT_JOB_KEY);
        String parametersJson = writeParameters(Map.of("recordId", record.getId()));
        String modified = record.getLastModifiedTime() == null
                ? ZonedDateTime.now().toString()
                : record.getLastModifiedTime().toString();
        JobExecution execution = jobExecutionService.createQueuedExecution(
                config,
                JobTriggerType.SYSTEM,
                record.getCreatedBy(),
                parametersJson,
                "QDRANT_UPSERT:%d:%s".formatted(record.getId(), modified)
        );
        jobSchedulerService.runExecutionAfterCommit(execution.getId());
        return execution;
    }

    @Transactional
    public JobExecution queueDelete(Long recordId) {
        ScheduledJobConfig config = getConfig(DELETE_JOB_KEY);
        JobExecution execution = jobExecutionService.createQueuedExecution(
                config,
                JobTriggerType.SYSTEM,
                null,
                writeParameters(Map.of("recordId", recordId)),
                "QDRANT_DELETE:%d".formatted(recordId)
        );
        jobSchedulerService.runExecutionAfterCommit(execution.getId());
        return execution;
    }

    private ScheduledJobConfig getConfig(String jobKey) {
        return scheduledJobConfigRepository.findByJobKey(jobKey)
                .orElseThrow(() -> new InvalidRequestException("Missing built-in Qdrant job config: " + jobKey));
    }

    private String writeParameters(Map<String, Object> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Unable to serialize Qdrant job parameters: " + e.getMessage());
        }
    }
}
