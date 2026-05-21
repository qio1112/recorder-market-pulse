package com.yipeng.recorder.job;

import com.yipeng.recorder.repository.JobExecutionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Map;

@Component
public class JobExecutionCleanupJobHandler implements JobHandler {

    private final JobExecutionRepository jobExecutionRepository;

    public JobExecutionCleanupJobHandler(JobExecutionRepository jobExecutionRepository) {
        this.jobExecutionRepository = jobExecutionRepository;
    }

    @Override
    public String jobType() {
        return JobType.JOB_EXECUTION_CLEANUP;
    }

    @Override
    @Transactional
    public JobResult run(JobContext context) {
        int deleted = jobExecutionRepository.deleteExpired(ZonedDateTime.now());
        return JobResult.of("Expired job executions deleted: " + deleted, Map.of("deletedCount", deleted));
    }
}
