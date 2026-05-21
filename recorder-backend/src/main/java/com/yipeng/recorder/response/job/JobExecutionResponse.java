package com.yipeng.recorder.response.job;

import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.utils.JobStatus;
import com.yipeng.recorder.utils.JobTriggerType;
import org.hibernate.Hibernate;

import java.time.ZonedDateTime;

public class JobExecutionResponse {

    private Long id;
    private Long jobConfigId;
    private String jobKey;
    private String jobType;
    private JobTriggerType triggerType;
    private JobStatus status;
    private int attempt;
    private int maxAttempts;
    private String idempotencyKey;
    private String parametersJson;
    private ZonedDateTime startedAt;
    private ZonedDateTime finishedAt;
    private Long durationMs;
    private String summary;
    private String detailsJson;
    private String errorType;
    private String errorMessage;
    private String retryStatusSummary;
    private ZonedDateTime retentionUntil;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static JobExecutionResponse from(JobExecution execution) {
        JobExecutionResponse response = new JobExecutionResponse();
        response.id = execution.getId();
        response.jobConfigId = execution.getJobConfig() == null || !Hibernate.isInitialized(execution.getJobConfig())
                ? null
                : execution.getJobConfig().getId();
        response.jobKey = execution.getJobKey();
        response.jobType = execution.getJobType();
        response.triggerType = execution.getTriggerType();
        response.status = execution.getStatus();
        response.attempt = execution.getAttempt();
        response.maxAttempts = execution.getMaxAttempts();
        response.idempotencyKey = execution.getIdempotencyKey();
        response.parametersJson = execution.getParametersJson();
        response.startedAt = execution.getStartedAt();
        response.finishedAt = execution.getFinishedAt();
        response.durationMs = execution.getDurationMs();
        response.summary = execution.getSummary();
        response.detailsJson = execution.getDetailsJson();
        response.errorType = execution.getErrorType();
        response.errorMessage = execution.getErrorMessage();
        response.retryStatusSummary = execution.getRetryStatusSummary();
        response.retentionUntil = execution.getRetentionUntil();
        response.createdAt = execution.getCreatedAt();
        response.updatedAt = execution.getUpdatedAt();
        return response;
    }

    public Long getId() { return id; }
    public Long getJobConfigId() { return jobConfigId; }
    public String getJobKey() { return jobKey; }
    public String getJobType() { return jobType; }
    public JobTriggerType getTriggerType() { return triggerType; }
    public JobStatus getStatus() { return status; }
    public int getAttempt() { return attempt; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getParametersJson() { return parametersJson; }
    public ZonedDateTime getStartedAt() { return startedAt; }
    public ZonedDateTime getFinishedAt() { return finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public String getSummary() { return summary; }
    public String getDetailsJson() { return detailsJson; }
    public String getErrorType() { return errorType; }
    public String getErrorMessage() { return errorMessage; }
    public String getRetryStatusSummary() { return retryStatusSummary; }
    public ZonedDateTime getRetentionUntil() { return retentionUntil; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
