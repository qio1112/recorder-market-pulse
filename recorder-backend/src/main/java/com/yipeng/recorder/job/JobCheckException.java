package com.yipeng.recorder.job;

import java.util.Map;

public class JobCheckException extends RuntimeException {

    private final Map<String, Object> details;

    public JobCheckException(String message, Map<String, Object> details) {
        super(message);
        this.details = details == null ? Map.of() : details;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
