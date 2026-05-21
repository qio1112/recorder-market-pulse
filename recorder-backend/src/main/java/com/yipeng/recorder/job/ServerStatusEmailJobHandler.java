package com.yipeng.recorder.job;

import com.yipeng.recorder.service.CronService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ServerStatusEmailJobHandler implements JobHandler {

    private final CronService cronService;

    public ServerStatusEmailJobHandler(CronService cronService) {
        this.cronService = cronService;
    }

    @Override
    public String jobType() {
        return JobType.SERVER_STATUS_EMAIL;
    }

    @Override
    public JobResult run(JobContext context) {
        String timeName = getStringParam(context, "timeName", "manual");
        cronService.serverStatusEmail(timeName);
        return JobResult.of("Server status email sent.", Map.of("timeName", timeName));
    }

    private String getStringParam(JobContext context, String key, String defaultValue) {
        Object value = context.parameters().get(key);
        return value == null ? defaultValue : value.toString();
    }
}
