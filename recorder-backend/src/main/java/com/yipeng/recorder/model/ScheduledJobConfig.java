package com.yipeng.recorder.model;

import com.yipeng.recorder.utils.JobScheduleType;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "scheduled_job_config",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_scheduled_job_key", columnNames = {"job_key"})
        }
)
public class ScheduledJobConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_key", nullable = false, length = 128)
    private String jobKey;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "job_type", nullable = false, length = 128)
    private String jobType;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 32)
    private JobScheduleType scheduleType;

    @Column(name = "cron_expression", length = 128)
    private String cronExpression;

    @Column(name = "interval_seconds")
    private Long intervalSeconds;

    @Column(length = 64)
    private String timezone;

    @Lob
    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "max_runtime_seconds")
    private Long maxRuntimeSeconds;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "retry_delay_seconds", nullable = false)
    private long retryDelaySeconds;

    @Column(name = "allow_concurrent_runs", nullable = false)
    private boolean allowConcurrentRuns;

    @Column(name = "next_run_at")
    private ZonedDateTime nextRunAt;

    @Column(name = "last_run_at")
    private ZonedDateTime lastRunAt;

    @Column(name = "is_builtin", nullable = false)
    private boolean builtin;

    @Column(name = "default_definition_version", nullable = false)
    private int defaultDefinitionVersion;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

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

    public String getJobKey() {
        return jobKey;
    }

    public void setJobKey(String jobKey) {
        this.jobKey = jobKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public JobScheduleType getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(JobScheduleType scheduleType) {
        this.scheduleType = scheduleType;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public Long getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(Long intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public Long getMaxRuntimeSeconds() {
        return maxRuntimeSeconds;
    }

    public void setMaxRuntimeSeconds(Long maxRuntimeSeconds) {
        this.maxRuntimeSeconds = maxRuntimeSeconds;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(long retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public boolean isAllowConcurrentRuns() {
        return allowConcurrentRuns;
    }

    public void setAllowConcurrentRuns(boolean allowConcurrentRuns) {
        this.allowConcurrentRuns = allowConcurrentRuns;
    }

    public ZonedDateTime getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(ZonedDateTime nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public ZonedDateTime getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(ZonedDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public void setBuiltin(boolean builtin) {
        this.builtin = builtin;
    }

    public int getDefaultDefinitionVersion() {
        return defaultDefinitionVersion;
    }

    public void setDefaultDefinitionVersion(int defaultDefinitionVersion) {
        this.defaultDefinitionVersion = defaultDefinitionVersion;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
