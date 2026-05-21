package com.yipeng.recorder.job;

import com.yipeng.recorder.exception.InvalidRequestException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JobDispatcher {

    private final Map<String, JobHandler> handlersByType;

    public JobDispatcher(java.util.List<JobHandler> handlers) {
        this.handlersByType = handlers.stream()
                .collect(Collectors.toMap(JobHandler::jobType, Function.identity()));
    }

    public JobResult dispatch(JobContext context) {
        JobHandler handler = handlersByType.get(context.config().getJobType());
        if (handler == null) {
            throw new InvalidRequestException("No job handler registered for type: " + context.config().getJobType());
        }
        return handler.run(context);
    }

    public boolean hasHandler(String jobType) {
        return handlersByType.containsKey(jobType);
    }
}
