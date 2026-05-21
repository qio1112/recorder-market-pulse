package com.yipeng.recorder.job;

import java.util.Collections;
import java.util.Map;

public record JobResult(String summary, Map<String, Object> details) {

    public static JobResult of(String summary) {
        return new JobResult(summary, Collections.emptyMap());
    }

    public static JobResult of(String summary, Map<String, Object> details) {
        return new JobResult(summary, details == null ? Collections.emptyMap() : details);
    }
}
