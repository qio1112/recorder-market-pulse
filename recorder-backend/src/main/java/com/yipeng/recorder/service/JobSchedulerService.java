package com.yipeng.recorder.service;

import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.model.ScheduledJobConfig;
import com.yipeng.recorder.repository.ScheduledJobConfigRepository;
import com.yipeng.recorder.utils.JobScheduleType;
import com.yipeng.recorder.utils.JobStatus;
import com.yipeng.recorder.utils.JobTriggerType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class JobSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(JobSchedulerService.class);

    private final ScheduledJobConfigRepository scheduledJobConfigRepository;
    private final JobExecutionService jobExecutionService;
    private final TaskExecutor taskExecutor;

    @Value("${jobs.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    public JobSchedulerService(ScheduledJobConfigRepository scheduledJobConfigRepository,
                               JobExecutionService jobExecutionService,
                               @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.scheduledJobConfigRepository = scheduledJobConfigRepository;
        this.jobExecutionService = jobExecutionService;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(fixedDelayString = "${jobs.scheduler.poll-delay-ms:30000}")
    public void pollDueJobs() {
        if (!schedulerEnabled) {
            return;
        }
        int timedOutCount = jobExecutionService.markStaleActiveExecutions();
        if (timedOutCount > 0) {
            logger.warn("Marked {} stale active job execution(s) as TIMEOUT", timedOutCount);
        }
        List<ScheduledJobConfig> dueJobs = scheduledJobConfigRepository.findDueJobs(
                ZonedDateTime.now(),
                JobScheduleType.MANUAL
        );
        for (ScheduledJobConfig config : dueJobs) {
            try {
                JobExecution execution = jobExecutionService.createQueuedExecution(config, JobTriggerType.SCHEDULED, null);
                jobExecutionService.updateConfigAfterScheduledRun(config);
                if (execution.getStatus() == JobStatus.QUEUED) {
                    runExecutionAfterCommit(execution.getId());
                }
            } catch (Exception e) {
                logger.error("Failed to queue scheduled job {}", config.getJobKey(), e);
            }
        }
    }

    @Async
    public void runExecutionAsync(Long executionId) {
        runExecutionSafely(executionId);
    }

    private void runExecutionSafely(Long executionId) {
        try {
            jobExecutionService.runExecution(executionId, null);
        } catch (Exception e) {
            logger.error("Failed to run job execution {}", executionId, e);
        }
    }

    public void runExecutionAfterCommit(Long executionId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            taskExecutor.execute(() -> runExecutionSafely(executionId));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.execute(() -> runExecutionSafely(executionId));
            }
        });
    }
}
