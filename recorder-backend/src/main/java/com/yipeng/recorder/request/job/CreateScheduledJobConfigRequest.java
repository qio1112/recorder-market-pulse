package com.yipeng.recorder.request.job;

import com.yipeng.recorder.utils.JobScheduleType;

public class CreateScheduledJobConfigRequest {

    private String jobKey;
    private String displayName;
    private String jobType;
    private Boolean enabled;
    private JobScheduleType scheduleType;
    private String cronExpression;
    private Long intervalSeconds;
    private String timezone;
    private String parametersJson;
    private Long maxRuntimeSeconds;
    private Integer retryCount;
    private Long retryDelaySeconds;
    private Boolean allowConcurrentRuns;
    private String description;

    public String getJobKey() { return jobKey; }
    public void setJobKey(String jobKey) { this.jobKey = jobKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public JobScheduleType getScheduleType() { return scheduleType; }
    public void setScheduleType(JobScheduleType scheduleType) { this.scheduleType = scheduleType; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public Long getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(Long intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
    public Long getMaxRuntimeSeconds() { return maxRuntimeSeconds; }
    public void setMaxRuntimeSeconds(Long maxRuntimeSeconds) { this.maxRuntimeSeconds = maxRuntimeSeconds; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Long getRetryDelaySeconds() { return retryDelaySeconds; }
    public void setRetryDelaySeconds(Long retryDelaySeconds) { this.retryDelaySeconds = retryDelaySeconds; }
    public Boolean getAllowConcurrentRuns() { return allowConcurrentRuns; }
    public void setAllowConcurrentRuns(Boolean allowConcurrentRuns) { this.allowConcurrentRuns = allowConcurrentRuns; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
