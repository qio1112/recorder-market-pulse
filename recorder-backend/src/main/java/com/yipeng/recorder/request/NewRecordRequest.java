package com.yipeng.recorder.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yipeng.recorder.utils.AlertType;
import com.yipeng.recorder.utils.DataUtils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewRecordRequest {

    private String title;
    private String content;
    private boolean isPublic;
    private List<String> labels;

    private AlertType alertType;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime alertTime;
    private String recurringAlertWeekDays;
    private Map<String, String> metadata = new HashMap<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public void setAlertType(AlertType alertType) {
        this.alertType = alertType;
    }

    public ZonedDateTime getAlertTime() {
        return alertTime;
    }

    public void setAlertTime(ZonedDateTime alertTime) {
        this.alertTime = alertTime;
    }

    public String getRecurringAlertWeekDays() {
        return recurringAlertWeekDays;
    }

    public void setRecurringAlertWeekDays(String recurringAlertWeekDays) {
        this.recurringAlertWeekDays = recurringAlertWeekDays;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = DataUtils.cleanMapKeysAndValues(metadata);
    }
}
