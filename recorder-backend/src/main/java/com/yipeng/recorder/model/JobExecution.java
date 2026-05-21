package com.yipeng.recorder.model;

import com.yipeng.recorder.utils.JobStatus;
import com.yipeng.recorder.utils.JobTriggerType;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "job_execution",
        indexes = {
                @Index(name = "idx_job_execution_job_key_started", columnList = "job_key,started_at"),
                @Index(name = "idx_job_execution_job_key_created", columnList = "job_key,created_at"),
                @Index(name = "idx_job_execution_status", columnList = "status"),
                @Index(name = "idx_job_execution_retention", columnList = "retention_until")
        }
)
public class JobExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_config_id")
    private ScheduledJobConfig jobConfig;

    @Column(name = "job_key", nullable = false, length = 128)
    private String jobKey;

    @Column(name = "job_type", nullable = false, length = 128)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 32)
    private JobTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Lob
    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "started_at")
    private ZonedDateTime startedAt;

    @Column(name = "finished_at")
    private ZonedDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Lob
    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "error_type", length = 512)
    private String errorType;

    @Lob
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Lob
    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Lob
    @Column(name = "retry_status_summary", columnDefinition = "TEXT")
    private String retryStatusSummary;

    @Column(name = "retention_until")
    private ZonedDateTime retentionUntil;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    void prePersist() {
        ZonedDateTime now = ZonedDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ScheduledJobConfig getJobConfig() {
        return jobConfig;
    }

    public void setJobConfig(ScheduledJobConfig jobConfig) {
        this.jobConfig = jobConfig;
    }

    public String getJobKey() {
        return jobKey;
    }

    public void setJobKey(String jobKey) {
        this.jobKey = jobKey;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public JobTriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(JobTriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(ZonedDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public ZonedDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(ZonedDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public String getRetryStatusSummary() {
        return retryStatusSummary;
    }

    public void setRetryStatusSummary(String retryStatusSummary) {
        this.retryStatusSummary = retryStatusSummary;
    }

    public ZonedDateTime getRetentionUntil() {
        return retentionUntil;
    }

    public void setRetentionUntil(ZonedDateTime retentionUntil) {
        this.retentionUntil = retentionUntil;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
