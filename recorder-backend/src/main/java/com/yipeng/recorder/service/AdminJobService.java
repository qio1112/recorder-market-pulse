package com.yipeng.recorder.service;

import com.yipeng.recorder.exception.ForbiddenException;
import com.yipeng.recorder.exception.InvalidRequestException;
import com.yipeng.recorder.job.BuiltInJobDefinitions;
import com.yipeng.recorder.job.JobDispatcher;
import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.model.ScheduledJobConfig;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.repository.ScheduledJobConfigRepository;
import com.yipeng.recorder.request.job.CreateScheduledJobConfigRequest;
import com.yipeng.recorder.request.job.UpdateScheduledJobConfigRequest;
import com.yipeng.recorder.utils.JobTriggerType;
import com.yipeng.recorder.utils.JobScheduleType;
import com.yipeng.recorder.response.job.JobExecutionResponse;
import com.yipeng.recorder.response.job.ScheduledJobConfigResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminJobService {

    private final ScheduledJobConfigRepository scheduledJobConfigRepository;
    private final JobExecutionService jobExecutionService;
    private final BuiltInJobSeeder builtInJobSeeder;
    private final JobSchedulerService jobSchedulerService;
    private final JobDispatcher jobDispatcher;

    public AdminJobService(ScheduledJobConfigRepository scheduledJobConfigRepository,
                           JobExecutionService jobExecutionService,
                           BuiltInJobSeeder builtInJobSeeder,
                           JobSchedulerService jobSchedulerService,
                           JobDispatcher jobDispatcher) {
        this.scheduledJobConfigRepository = scheduledJobConfigRepository;
        this.jobExecutionService = jobExecutionService;
        this.builtInJobSeeder = builtInJobSeeder;
        this.jobSchedulerService = jobSchedulerService;
        this.jobDispatcher = jobDispatcher;
    }

    @Transactional(readOnly = true)
    public List<ScheduledJobConfig> listConfigs() {
        return scheduledJobConfigRepository.findAllByOrderByJobKeyAsc();
    }

    @Transactional(readOnly = true)
    public ScheduledJobConfig getConfig(Long id) {
        return scheduledJobConfigRepository.findById(id)
                .orElseThrow(() -> new InvalidRequestException("Job config not found: " + id));
    }

    @Transactional
    public ScheduledJobConfig updateConfig(Long id, UpdateScheduledJobConfigRequest request) {
        ScheduledJobConfig config = getConfig(id);
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        if (request.getCronExpression() != null) {
            config.setCronExpression(request.getCronExpression());
        }
        if (request.getIntervalSeconds() != null) {
            config.setIntervalSeconds(request.getIntervalSeconds());
        }
        if (request.getParametersJson() != null) {
            config.setParametersJson(request.getParametersJson());
        }
        if (request.getMaxRuntimeSeconds() != null) {
            config.setMaxRuntimeSeconds(request.getMaxRuntimeSeconds());
        }
        if (request.getRetryCount() != null) {
            config.setRetryCount(request.getRetryCount());
        }
        if (request.getRetryDelaySeconds() != null) {
            config.setRetryDelaySeconds(request.getRetryDelaySeconds());
        }
        if (request.getAllowConcurrentRuns() != null) {
            config.setAllowConcurrentRuns(request.getAllowConcurrentRuns());
        }
        config.setNextRunAt(builtInJobSeeder.computeNextRunAt(config, ZonedDateTime.now()));
        return scheduledJobConfigRepository.save(config);
    }

    @Transactional
    public ScheduledJobConfig createConfig(CreateScheduledJobConfigRequest request) {
        validateCreateRequest(request);
        scheduledJobConfigRepository.findByJobKey(request.getJobKey()).ifPresent(existing -> {
            throw new InvalidRequestException("Job key already exists: " + existing.getJobKey());
        });

        ScheduledJobConfig config = new ScheduledJobConfig();
        config.setJobKey(request.getJobKey());
        config.setDisplayName(request.getDisplayName());
        config.setJobType(request.getJobType());
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        config.setScheduleType(request.getScheduleType());
        config.setCronExpression(request.getCronExpression());
        config.setIntervalSeconds(request.getIntervalSeconds());
        config.setTimezone(StringUtils.defaultIfBlank(request.getTimezone(), BuiltInJobDefinitions.DEFAULT_TIMEZONE));
        config.setParametersJson(StringUtils.defaultIfBlank(request.getParametersJson(), "{}"));
        config.setMaxRuntimeSeconds(request.getMaxRuntimeSeconds());
        config.setRetryCount(request.getRetryCount() == null ? 0 : request.getRetryCount());
        config.setRetryDelaySeconds(request.getRetryDelaySeconds() == null ? 0L : request.getRetryDelaySeconds());
        config.setAllowConcurrentRuns(Boolean.TRUE.equals(request.getAllowConcurrentRuns()));
        config.setBuiltin(false);
        config.setDefaultDefinitionVersion(1);
        config.setDescription(request.getDescription());
        config.setNextRunAt(builtInJobSeeder.computeNextRunAt(config, ZonedDateTime.now()));
        return scheduledJobConfigRepository.save(config);
    }

    @Transactional
    public void deleteConfig(Long id) {
        ScheduledJobConfig config = getConfig(id);
        if (config.isBuiltin()) {
            throw new ForbiddenException("Built-in jobs cannot be deleted. Disable the job instead.");
        }
        scheduledJobConfigRepository.delete(config);
    }

    @Transactional
    public JobExecution triggerJob(Long configId, User adminUser) {
        ScheduledJobConfig config = getConfig(configId);
        JobExecution execution = jobExecutionService.createQueuedExecution(config, JobTriggerType.MANUAL, adminUser);
        jobSchedulerService.runExecutionAfterCommit(execution.getId());
        return execution;
    }

    @Transactional(readOnly = true)
    public Page<JobExecution> listExecutions(Pageable pageable) {
        return jobExecutionService.listExecutions(pageable);
    }

    @Transactional(readOnly = true)
    public JobExecution getExecution(Long id) {
        return jobExecutionService.getExecution(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard() {
        List<ScheduledJobConfig> jobConfigs = listConfigs();
        List<ScheduledJobConfigResponse> configs = jobConfigs.stream()
                .map(ScheduledJobConfigResponse::from)
                .toList();
        Map<String, JobExecutionResponse> latestByJobKey = new LinkedHashMap<>();
        for (ScheduledJobConfig config : jobConfigs) {
            jobExecutionService.getLatestExecution(config.getJobKey())
                    .map(JobExecutionResponse::from)
                    .ifPresent(response -> latestByJobKey.put(config.getJobKey(), response));
        }
        Map<String, List<JobExecutionResponse>> historyByJobType = new LinkedHashMap<>();
        Map<String, List<ScheduledJobConfig>> configsByJobType = jobConfigs.stream()
                .collect(Collectors.groupingBy(ScheduledJobConfig::getJobType, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<ScheduledJobConfig>> entry : configsByJobType.entrySet()) {
            List<String> jobKeys = entry.getValue().stream()
                    .map(ScheduledJobConfig::getJobKey)
                    .toList();
            List<JobExecutionResponse> executions = jobExecutionService
                    .listRecentExecutionsForJobKeys(jobKeys, PageRequest.of(0, 30))
                    .stream()
                    .map(JobExecutionResponse::from)
                    .toList();
            historyByJobType.put(entry.getKey(), executions);
        }
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("configs", configs);
        dashboard.put("latestByJobKey", latestByJobKey);
        dashboard.put("historyByJobType", historyByJobType);
        return dashboard;
    }

    private void validateCreateRequest(CreateScheduledJobConfigRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Job config request is required.");
        }
        if (StringUtils.isBlank(request.getJobKey())) {
            throw new InvalidRequestException("jobKey is required.");
        }
        if (StringUtils.isBlank(request.getDisplayName())) {
            throw new InvalidRequestException("displayName is required.");
        }
        if (StringUtils.isBlank(request.getJobType()) || !jobDispatcher.hasHandler(request.getJobType())) {
            throw new InvalidRequestException("Registered jobType is required.");
        }
        if (request.getScheduleType() == null) {
            throw new InvalidRequestException("scheduleType is required.");
        }
        if (request.getScheduleType() == JobScheduleType.CRON && StringUtils.isBlank(request.getCronExpression())) {
            throw new InvalidRequestException("cronExpression is required for CRON jobs.");
        }
        if (request.getScheduleType() == JobScheduleType.FIXED_INTERVAL
                && (request.getIntervalSeconds() == null || request.getIntervalSeconds() <= 0)) {
            throw new InvalidRequestException("intervalSeconds must be positive for FIXED_INTERVAL jobs.");
        }
    }
}
