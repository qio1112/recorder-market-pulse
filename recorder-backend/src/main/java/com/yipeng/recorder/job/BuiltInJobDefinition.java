package com.yipeng.recorder.job;

import com.yipeng.recorder.utils.JobScheduleType;

public record BuiltInJobDefinition(
        String jobKey,
        String displayName,
        String jobType,
        boolean enabled,
        JobScheduleType scheduleType,
        String cronExpression,
        Long intervalSeconds,
        String timezone,
        String parametersJson,
        Long maxRuntimeSeconds,
        int retryCount,
        long retryDelaySeconds,
        boolean allowConcurrentRuns,
        int defaultDefinitionVersion,
        String description
) {
}
