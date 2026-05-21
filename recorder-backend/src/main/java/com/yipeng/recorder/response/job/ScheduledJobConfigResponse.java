package com.yipeng.recorder.response.job;

import com.yipeng.recorder.model.ScheduledJobConfig;
import com.yipeng.recorder.utils.JobScheduleType;

import java.time.ZonedDateTime;

public class ScheduledJobConfigResponse {

    private Long id;
    private String jobKey;
    private String displayName;
    private String jobType;
    private boolean enabled;
    private JobScheduleType scheduleType;
    private String cronExpression;
    private Long intervalSeconds;
    private String timezone;
    private String parametersJson;
    private Long maxRuntimeSeconds;
    private int retryCount;
    private long retryDelaySeconds;
    private boolean allowConcurrentRuns;
    private ZonedDateTime nextRunAt;
    private ZonedDateTime lastRunAt;
    private boolean builtin;
    private int defaultDefinitionVersion;
    private String description;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static ScheduledJobConfigResponse from(ScheduledJobConfig config) {
        ScheduledJobConfigResponse response = new ScheduledJobConfigResponse();
        response.id = config.getId();
        response.jobKey = config.getJobKey();
        response.displayName = config.getDisplayName();
        response.jobType = config.getJobType();
        response.enabled = config.isEnabled();
        response.scheduleType = config.getScheduleType();
        response.cronExpression = config.getCronExpression();
        response.intervalSeconds = config.getIntervalSeconds();
        response.timezone = config.getTimezone();
        response.parametersJson = config.getParametersJson();
        response.maxRuntimeSeconds = config.getMaxRuntimeSeconds();
        response.retryCount = config.getRetryCount();
        response.retryDelaySeconds = config.getRetryDelaySeconds();
        response.allowConcurrentRuns = config.isAllowConcurrentRuns();
        response.nextRunAt = config.getNextRunAt();
        response.lastRunAt = config.getLastRunAt();
        response.builtin = config.isBuiltin();
        response.defaultDefinitionVersion = config.getDefaultDefinitionVersion();
        response.description = config.getDescription();
        response.createdAt = config.getCreatedAt();
        response.updatedAt = config.getUpdatedAt();
        return response;
    }

    public Long getId() { return id; }
    public String getJobKey() { return jobKey; }
    public String getDisplayName() { return displayName; }
    public String getJobType() { return jobType; }
    public boolean isEnabled() { return enabled; }
    public JobScheduleType getScheduleType() { return scheduleType; }
    public String getCronExpression() { return cronExpression; }
    public Long getIntervalSeconds() { return intervalSeconds; }
    public String getTimezone() { return timezone; }
    public String getParametersJson() { return parametersJson; }
    public Long getMaxRuntimeSeconds() { return maxRuntimeSeconds; }
    public int getRetryCount() { return retryCount; }
    public long getRetryDelaySeconds() { return retryDelaySeconds; }
    public boolean isAllowConcurrentRuns() { return allowConcurrentRuns; }
    public ZonedDateTime getNextRunAt() { return nextRunAt; }
    public ZonedDateTime getLastRunAt() { return lastRunAt; }
    public boolean isBuiltin() { return builtin; }
    public int getDefaultDefinitionVersion() { return defaultDefinitionVersion; }
    public String getDescription() { return description; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
