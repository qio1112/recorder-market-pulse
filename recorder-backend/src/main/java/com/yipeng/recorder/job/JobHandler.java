package com.yipeng.recorder.job;

public interface JobHandler {

    String jobType();

    JobResult run(JobContext context);
}
