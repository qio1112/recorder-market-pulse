package com.yipeng.recorder.request.job;

public class UpdateScheduledJobConfigRequest {

    private Boolean enabled;
    private String cronExpression;
    private Long intervalSeconds;
    private String parametersJson;
    private Long maxRuntimeSeconds;
    private Integer retryCount;
    private Long retryDelaySeconds;
    private Boolean allowConcurrentRuns;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Long getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(Long retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public Boolean getAllowConcurrentRuns() {
        return allowConcurrentRuns;
    }

    public void setAllowConcurrentRuns(Boolean allowConcurrentRuns) {
        this.allowConcurrentRuns = allowConcurrentRuns;
    }
}
