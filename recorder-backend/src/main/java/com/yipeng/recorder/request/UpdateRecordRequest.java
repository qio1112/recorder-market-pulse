package com.yipeng.recorder.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.yipeng.recorder.utils.AlertType;
import com.yipeng.recorder.utils.DataUtils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateRecordRequest {

    private Long id;
    private String title;
    private String content;
    private boolean isPublic;
    private List<String> labels = new ArrayList<>(); // this contains all labels
    private List<Long> removeFileIDs = new ArrayList<>();

    private boolean cancelAlert;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<Long> getRemoveFileIDs() {
        return removeFileIDs;
    }

    public void setRemoveFileIDs(List<Long> removeFileIDs) {
        this.removeFileIDs = removeFileIDs;
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

    public boolean isCancelAlert() {
        return cancelAlert;
    }

    public void setCancelAlert(boolean cancelAlert) {
        this.cancelAlert = cancelAlert;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = DataUtils.cleanMapKeysAndValues(metadata);
    }
}
