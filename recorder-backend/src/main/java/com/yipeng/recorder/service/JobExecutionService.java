package com.yipeng.recorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.recorder.exception.InvalidRequestException;
import com.yipeng.recorder.job.JobContext;
import com.yipeng.recorder.job.JobCheckException;
import com.yipeng.recorder.job.JobDispatcher;
import com.yipeng.recorder.job.JobResult;
import com.yipeng.recorder.job.JobType;
import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.model.ScheduledJobConfig;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.repository.JobExecutionRepository;
import com.yipeng.recorder.repository.ScheduledJobConfigRepository;
import com.yipeng.recorder.utils.JobStatus;
import com.yipeng.recorder.utils.JobTriggerType;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class JobExecutionService {

    private static final int RETENTION_DAYS = 30;
    private static final int STACK_TRACE_LIMIT = 12000;
    private static final long DEFAULT_STALE_EXECUTION_SECONDS = 3600L;

    private final ScheduledJobConfigRepository scheduledJobConfigRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobDispatcher jobDispatcher;
    private final BuiltInJobSeeder builtInJobSeeder;
    private final SendEmailService sendEmailService;
    private final ObjectMapper objectMapper;

    @Value("${admin.email}")
    private String adminEmail;

    public JobExecutionService(ScheduledJobConfigRepository scheduledJobConfigRepository,
                               JobExecutionRepository jobExecutionRepository,
                               JobDispatcher jobDispatcher,
                               BuiltInJobSeeder builtInJobSeeder,
                               SendEmailService sendEmailService,
                               ObjectMapper objectMapper) {
        this.scheduledJobConfigRepository = scheduledJobConfigRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.jobDispatcher = jobDispatcher;
        this.builtInJobSeeder = builtInJobSeeder;
        this.sendEmailService = sendEmailService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<JobExecution> listExecutions(Pageable pageable) {
        return jobExecutionRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<JobExecution> listRecentExecutions() {
        return jobExecutionRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<JobExecution> listRecentExecutionsForJobKeys(List<String> jobKeys, Pageable pageable) {
        if (jobKeys == null || jobKeys.isEmpty()) {
            return List.of();
        }
        return jobExecutionRepository.findByJobKeyInOrderByCreatedAtDesc(jobKeys, pageable).getContent();
    }

    @Transactional(readOnly = true)
    public Optional<JobExecution> getLatestExecution(String jobKey) {
        return jobExecutionRepository.findFirstByJobKeyOrderByCreatedAtDesc(jobKey);
    }

    @Transactional(readOnly = true)
    public JobExecution getExecution(Long id) {
        return jobExecutionRepository.findByIdWithJobConfig(id)
                .orElseThrow(() -> new InvalidRequestException("Job execution not found: " + id));
    }

    @Transactional
    public JobExecution createQueuedExecution(ScheduledJobConfig config, JobTriggerType triggerType, User triggeredBy) {
        return createQueuedExecution(config, triggerType, triggeredBy, config.getParametersJson(), null);
    }

    @Transactional
    public JobExecution createQueuedExecution(ScheduledJobConfig config,
                                              JobTriggerType triggerType,
                                              User triggeredBy,
                                              String parametersJson,
                                              String idempotencyKey) {
        markStaleExecutionsForJobKey(config.getJobKey());
        if (!config.isAllowConcurrentRuns() && isJobRunning(config.getJobKey())) {
            JobExecution skipped = createBaseExecution(config, triggerType, parametersJson, idempotencyKey);
            skipped.setStatus(JobStatus.SKIPPED);
            skipped.setStartedAt(ZonedDateTime.now());
            skipped.setFinishedAt(skipped.getStartedAt());
            skipped.setDurationMs(0L);
            skipped.setSummary("Skipped because another execution is already running.");
            return jobExecutionRepository.save(skipped);
        }
        JobExecution execution = createBaseExecution(config, triggerType, parametersJson, idempotencyKey);
        execution.setStatus(JobStatus.QUEUED);
        return jobExecutionRepository.save(execution);
    }

    public JobExecution runExecution(Long executionId, User triggeredBy) {
        startExecution(executionId);
        JobExecution execution = getExecution(executionId);
        if (execution.getStatus() != JobStatus.RUNNING) {
            return execution;
        }

        try {
            ScheduledJobConfig config = execution.getJobConfig();
            Map<String, Object> parameters = parseParameters(execution.getParametersJson());
            JobResult result = jobDispatcher.dispatch(new JobContext(
                    config,
                    execution,
                    parameters,
                    execution.getTriggerType(),
                    triggeredBy
            ));
            return markSuccess(execution.getId(), result);
        } catch (Exception e) {
            return markFailureWithRetry(execution.getId(), e, triggeredBy);
        }
    }

    @Transactional
    public void updateConfigAfterScheduledRun(ScheduledJobConfig config) {
        ScheduledJobConfig attached = scheduledJobConfigRepository.findById(config.getId())
                .orElseThrow(() -> new InvalidRequestException("Job config not found: " + config.getId()));
        ZonedDateTime now = ZonedDateTime.now();
        attached.setLastRunAt(now);
        attached.setNextRunAt(builtInJobSeeder.computeNextRunAt(attached, now));
        scheduledJobConfigRepository.save(attached);
    }

    @Transactional
    public int cleanupExpiredExecutions() {
        return jobExecutionRepository.deleteExpired(ZonedDateTime.now());
    }

    @Transactional
    public int markStaleActiveExecutions() {
        int updatedCount = 0;
        for (JobExecution execution : jobExecutionRepository.findByStatusInWithJobConfig(activeStatuses())) {
            if (markTimeoutIfStale(execution, ZonedDateTime.now())) {
                updatedCount += 1;
            }
        }
        return updatedCount;
    }

    @Transactional
    public int markStaleExecutionsForJobKey(String jobKey) {
        int updatedCount = 0;
        ZonedDateTime now = ZonedDateTime.now();
        for (JobExecution execution : jobExecutionRepository.findByJobKeyAndStatusInWithJobConfig(jobKey, activeStatuses())) {
            if (markTimeoutIfStale(execution, now)) {
                updatedCount += 1;
            }
        }
        return updatedCount;
    }

    private boolean isJobRunning(String jobKey) {
        return jobExecutionRepository.existsByJobKeyAndStatusIn(jobKey, EnumSet.of(JobStatus.QUEUED, JobStatus.RUNNING));
    }

    private EnumSet<JobStatus> activeStatuses() {
        return EnumSet.of(JobStatus.QUEUED, JobStatus.RUNNING);
    }

    private boolean markTimeoutIfStale(JobExecution execution, ZonedDateTime now) {
        ZonedDateTime referenceTime = execution.getStartedAt() == null ? execution.getCreatedAt() : execution.getStartedAt();
        if (referenceTime == null || !referenceTime.plusSeconds(staleThresholdSeconds(execution)).isBefore(now)) {
            return false;
        }
        execution.setStatus(JobStatus.TIMEOUT);
        execution.setFinishedAt(now);
        execution.setDurationMs(computeDurationMs(referenceTime, now));
        execution.setSummary("Execution timed out because it stayed active past maxRuntimeSeconds.");
        execution.setErrorType("com.yipeng.recorder.job.StaleJobExecutionTimeout");
        execution.setErrorMessage("Execution was marked TIMEOUT during stale active job cleanup.");
        jobExecutionRepository.save(execution);
        return true;
    }

    private long staleThresholdSeconds(JobExecution execution) {
        ScheduledJobConfig config = execution.getJobConfig();
        if (config == null || config.getMaxRuntimeSeconds() == null || config.getMaxRuntimeSeconds() <= 0) {
            return DEFAULT_STALE_EXECUTION_SECONDS;
        }
        return config.getMaxRuntimeSeconds();
    }

    private JobExecution createBaseExecution(ScheduledJobConfig config, JobTriggerType triggerType) {
        return createBaseExecution(config, triggerType, config.getParametersJson(), null);
    }

    private JobExecution createBaseExecution(ScheduledJobConfig config,
                                             JobTriggerType triggerType,
                                             String parametersJson,
                                             String idempotencyKey) {
        JobExecution execution = new JobExecution();
        execution.setJobConfig(config);
        execution.setJobKey(config.getJobKey());
        execution.setJobType(config.getJobType());
        execution.setTriggerType(triggerType);
        execution.setAttempt(1);
        execution.setMaxAttempts(Math.max(1, config.getRetryCount() + 1));
        execution.setParametersJson(parametersJson);
        execution.setIdempotencyKey(StringUtils.defaultIfBlank(idempotencyKey, config.getJobKey() + ":" + System.currentTimeMillis()));
        execution.setRetentionUntil(ZonedDateTime.now().plusDays(RETENTION_DAYS));
        return execution;
    }

    @Transactional
    protected JobExecution startExecution(Long executionId) {
        JobExecution execution = getExecution(executionId);
        if (execution.getStatus() != JobStatus.QUEUED) {
            return execution;
        }
        execution.setStatus(JobStatus.RUNNING);
        execution.setStartedAt(ZonedDateTime.now());
        return jobExecutionRepository.save(execution);
    }

    @Transactional
    protected JobExecution markSuccess(Long executionId, JobResult result) {
        JobExecution execution = getExecution(executionId);
        if (execution.getStatus() != JobStatus.RUNNING) {
            return execution;
        }
        ZonedDateTime finishedAt = ZonedDateTime.now();
        execution.setStatus(JobStatus.SUCCESS);
        execution.setFinishedAt(finishedAt);
        execution.setDurationMs(computeDurationMs(execution.getStartedAt(), finishedAt));
        execution.setSummary(StringUtils.defaultIfBlank(result.summary(), "Job completed successfully."));
        execution.setDetailsJson(writeJson(result.details()));
        return jobExecutionRepository.save(execution);
    }

    @Transactional
    protected JobExecution markFailureWithRetry(Long executionId, Exception e, User triggeredBy) {
        JobExecution execution = getExecution(executionId);
        if (execution.getStatus() != JobStatus.RUNNING) {
            return execution;
        }
        ZonedDateTime finishedAt = ZonedDateTime.now();
        execution.setFinishedAt(finishedAt);
        execution.setDurationMs(computeDurationMs(execution.getStartedAt(), finishedAt));
        execution.setErrorType(e.getClass().getName());
        execution.setErrorMessage(StringUtils.defaultIfBlank(e.getMessage(), "(no error message)"));
        execution.setStackTrace(StringUtils.left(ExceptionUtils.getStackTrace(e), STACK_TRACE_LIMIT));
        if (e instanceof JobCheckException checkException) {
            execution.setDetailsJson(writeJson(checkException.getDetails()));
        }

        if (execution.getAttempt() < execution.getMaxAttempts()) {
            execution.setStatus(JobStatus.RETRYING);
            execution.setRetryStatusSummary("Attempt %d of %d failed. Retrying after configured delay.".formatted(
                    execution.getAttempt(),
                    execution.getMaxAttempts()
            ));
            jobExecutionRepository.save(execution);
            JobExecution retry = createBaseExecution(execution.getJobConfig(), JobTriggerType.RETRY);
            retry.setAttempt(execution.getAttempt() + 1);
            retry.setMaxAttempts(execution.getMaxAttempts());
            retry.setIdempotencyKey(execution.getIdempotencyKey());
            retry.setRetryStatusSummary("Retry attempt %d of %d created after execution %d failed.".formatted(
                    retry.getAttempt(),
                    retry.getMaxAttempts(),
                    execution.getId()
            ));
            JobExecution savedRetry = jobExecutionRepository.save(retry);
            return runExecution(savedRetry.getId(), triggeredBy);
        }

        execution.setStatus(JobStatus.FAILED);
        execution.setRetryStatusSummary("Final failure after %d attempt(s).".formatted(execution.getAttempt()));
        JobExecution saved = jobExecutionRepository.save(execution);
        sendFinalFailureEmail(saved);
        return saved;
    }

    private void sendFinalFailureEmail(JobExecution execution) {
        if (JobType.isStatusCheckType(execution.getJobType())) {
            return;
        }
        String subject = "FAILED: Job %s after %d attempt(s)".formatted(execution.getJobKey(), execution.getAttempt());
        String content = """
                Job failed after retries were exhausted.

                Job: %s
                Execution ID: %d
                Status: %s
                Attempt: %d of %d
                Retry status: %s
                Error type: %s
                Error: %s
                """.formatted(
                execution.getJobKey(),
                execution.getId(),
                execution.getStatus(),
                execution.getAttempt(),
                execution.getMaxAttempts(),
                StringUtils.defaultIfBlank(execution.getRetryStatusSummary(), "(none)"),
                StringUtils.defaultIfBlank(execution.getErrorType(), "(unknown)"),
                StringUtils.defaultIfBlank(execution.getErrorMessage(), "(no error message)")
        );
        try {
            sendEmailService.sendEmail(adminEmail, subject, content, null);
        } catch (Exception emailError) {
            // The job result is already persisted; email failure should not mask the original job failure.
        }
    }

    private Map<String, Object> parseParameters(String parametersJson) {
        if (StringUtils.isBlank(parametersJson)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(parametersJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Invalid job parameters JSON: " + e.getMessage());
        }
    }

    private String writeJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Long computeDurationMs(ZonedDateTime startedAt, ZonedDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }
}
