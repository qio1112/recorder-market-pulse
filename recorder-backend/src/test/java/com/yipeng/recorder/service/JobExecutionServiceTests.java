package com.yipeng.recorder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.recorder.job.JobDispatcher;
import com.yipeng.recorder.job.JobResult;
import com.yipeng.recorder.job.JobType;
import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.model.ScheduledJobConfig;
import com.yipeng.recorder.repository.JobExecutionRepository;
import com.yipeng.recorder.repository.ScheduledJobConfigRepository;
import com.yipeng.recorder.utils.JobStatus;
import com.yipeng.recorder.utils.JobTriggerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobExecutionServiceTests {

    @Test
    void statusCheckFinalFailureDoesNotSendEmail() throws Exception {
        SendEmailService sendEmailService = mock(SendEmailService.class);
        JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
        JobExecutionService service = createService(jobExecutionRepository, sendEmailService);
        JobExecution execution = failedExecution(JobType.MARKET_PULSE_HEALTH_CHECK);
        when(jobExecutionRepository.findByIdWithJobConfig(100L)).thenReturn(Optional.of(execution));
        when(jobExecutionRepository.save(any(JobExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markFailureWithRetry(100L, new RuntimeException("market pulse down"), null);

        verify(sendEmailService, never()).sendEmail(any(), any(), any(), any());
    }

    @Test
    void nonStatusCheckFinalFailureSendsEmail() throws Exception {
        SendEmailService sendEmailService = mock(SendEmailService.class);
        JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
        JobExecutionService service = createService(jobExecutionRepository, sendEmailService);
        JobExecution execution = failedExecution(JobType.STOCK_OPTION_DATA_UPDATE);
        when(jobExecutionRepository.findByIdWithJobConfig(100L)).thenReturn(Optional.of(execution));
        when(jobExecutionRepository.save(any(JobExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markFailureWithRetry(100L, new RuntimeException("update failed"), null);

        verify(sendEmailService).sendEmail(
                eq("admin@example.com"),
                startsWith("FAILED: Job test-job after 1 attempt"),
                contains("update failed"),
                isNull()
        );
    }

    @Test
    void staleRunningExecutionIsMarkedTimeout() {
        SendEmailService sendEmailService = mock(SendEmailService.class);
        JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
        JobExecutionService service = createService(jobExecutionRepository, sendEmailService);
        JobExecution execution = failedExecution(JobType.OPTION_DATA_FRESHNESS_CHECK);
        ScheduledJobConfig config = new ScheduledJobConfig();
        config.setMaxRuntimeSeconds(60L);
        execution.setJobConfig(config);
        execution.setStartedAt(ZonedDateTime.now().minusMinutes(5));
        when(jobExecutionRepository.findByStatusInWithJobConfig(any())).thenReturn(List.of(execution));
        when(jobExecutionRepository.save(any(JobExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int updatedCount = service.markStaleActiveExecutions();

        org.junit.jupiter.api.Assertions.assertEquals(1, updatedCount);
        org.junit.jupiter.api.Assertions.assertEquals(JobStatus.TIMEOUT, execution.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(execution.getSummary().contains("timed out"));
    }

    @Test
    void markSuccessDoesNotOverwriteTimedOutExecution() {
        SendEmailService sendEmailService = mock(SendEmailService.class);
        JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
        JobExecutionService service = createService(jobExecutionRepository, sendEmailService);
        JobExecution execution = failedExecution(JobType.OPTION_DATA_FRESHNESS_CHECK);
        execution.setStatus(JobStatus.TIMEOUT);
        when(jobExecutionRepository.findByIdWithJobConfig(100L)).thenReturn(Optional.of(execution));

        service.markSuccess(100L, JobResult.of("done", Map.of()));

        org.junit.jupiter.api.Assertions.assertEquals(JobStatus.TIMEOUT, execution.getStatus());
        verify(jobExecutionRepository, never()).save(any(JobExecution.class));
    }

    @Test
    void retryExecutionIsCreatedWithQueuedStatus() {
        SendEmailService sendEmailService = mock(SendEmailService.class);
        JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
        JobDispatcher jobDispatcher = mock(JobDispatcher.class);
        JobExecutionService service = createService(jobExecutionRepository, sendEmailService, jobDispatcher);
        ScheduledJobConfig config = new ScheduledJobConfig();
        config.setJobKey("stock-after-close-refresh-2100");
        config.setJobType(JobType.STOCK_AFTER_CLOSE_REFRESH);
        config.setRetryCount(1);
        config.setParametersJson("{\"timeName\":\"21:00\"}");

        JobExecution firstAttempt = failedExecution(JobType.STOCK_AFTER_CLOSE_REFRESH);
        firstAttempt.setJobConfig(config);
        firstAttempt.setMaxAttempts(2);
        Map<Long, JobExecution> executions = new HashMap<>();
        executions.put(100L, firstAttempt);
        AtomicBoolean savedQueuedRetry = new AtomicBoolean(false);

        when(jobExecutionRepository.findByIdWithJobConfig(any())).thenAnswer(invocation ->
                Optional.ofNullable(executions.get(invocation.getArgument(0)))
        );
        when(jobExecutionRepository.save(any(JobExecution.class))).thenAnswer(invocation -> {
            JobExecution execution = invocation.getArgument(0);
            if (execution.getId() == null) {
                if (execution.getAttempt() == 2 && execution.getStatus() == JobStatus.QUEUED) {
                    savedQueuedRetry.set(true);
                }
                execution.setId(200L);
            }
            executions.put(execution.getId(), execution);
            return execution;
        });
        when(jobDispatcher.dispatch(any())).thenReturn(JobResult.of("retry succeeded", Map.of()));

        service.markFailureWithRetry(100L, new RuntimeException("transient stock update failure"), null);

        JobExecution retry = executions.get(200L);
        org.junit.jupiter.api.Assertions.assertTrue(savedQueuedRetry.get());
        org.junit.jupiter.api.Assertions.assertEquals(JobStatus.SUCCESS, retry.getStatus());
    }

    @Test
    void retryInsertFailureDoesNotPersistOriginalAsRetrying() {
        SendEmailService sendEmailService = mock(SendEmailService.class);
        JobExecutionRepository jobExecutionRepository = mock(JobExecutionRepository.class);
        JobExecutionService service = createService(jobExecutionRepository, sendEmailService);
        ScheduledJobConfig config = new ScheduledJobConfig();
        config.setJobKey("stock-after-close-refresh-2100");
        config.setJobType(JobType.STOCK_AFTER_CLOSE_REFRESH);
        config.setRetryCount(1);

        JobExecution firstAttempt = failedExecution(JobType.STOCK_AFTER_CLOSE_REFRESH);
        firstAttempt.setJobConfig(config);
        firstAttempt.setMaxAttempts(2);

        when(jobExecutionRepository.findByIdWithJobConfig(100L)).thenReturn(Optional.of(firstAttempt));
        when(jobExecutionRepository.save(any(JobExecution.class))).thenAnswer(invocation -> {
            JobExecution execution = invocation.getArgument(0);
            if (execution.getAttempt() == 2) {
                throw new RuntimeException("retry insert failed");
            }
            return execution;
        });

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> service.markFailureWithRetry(100L, new RuntimeException("transient stock update failure"), null)
        );

        org.junit.jupiter.api.Assertions.assertEquals(JobStatus.RUNNING, firstAttempt.getStatus());
        verify(jobExecutionRepository, times(1)).save(any(JobExecution.class));
    }

    private JobExecutionService createService(JobExecutionRepository jobExecutionRepository,
                                              SendEmailService sendEmailService) {
        return createService(jobExecutionRepository, sendEmailService, mock(JobDispatcher.class));
    }

    private JobExecutionService createService(JobExecutionRepository jobExecutionRepository,
                                              SendEmailService sendEmailService,
                                              JobDispatcher jobDispatcher) {
        JobExecutionService service = new JobExecutionService(
                mock(ScheduledJobConfigRepository.class),
                jobExecutionRepository,
                jobDispatcher,
                mock(BuiltInJobSeeder.class),
                sendEmailService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "adminEmail", "admin@example.com");
        return service;
    }

    private JobExecution failedExecution(String jobType) {
        JobExecution execution = new JobExecution();
        execution.setId(100L);
        execution.setJobKey("test-job");
        execution.setJobType(jobType);
        execution.setTriggerType(JobTriggerType.MANUAL);
        execution.setStatus(JobStatus.RUNNING);
        execution.setAttempt(1);
        execution.setMaxAttempts(1);
        execution.setStartedAt(ZonedDateTime.now().minusSeconds(1));
        return execution;
    }
}
