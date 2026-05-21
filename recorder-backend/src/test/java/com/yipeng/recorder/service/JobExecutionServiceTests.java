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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private JobExecutionService createService(JobExecutionRepository jobExecutionRepository,
                                              SendEmailService sendEmailService) {
        JobExecutionService service = new JobExecutionService(
                mock(ScheduledJobConfigRepository.class),
                jobExecutionRepository,
                mock(JobDispatcher.class),
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
