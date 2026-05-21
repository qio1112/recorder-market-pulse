package com.yipeng.recorder.job;

import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.model.ScheduledJobConfig;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.utils.JobTriggerType;

import java.util.Map;

public record JobContext(
        ScheduledJobConfig config,
        JobExecution execution,
        Map<String, Object> parameters,
        JobTriggerType triggerType,
        User triggeredBy
) {
}
