package com.yipeng.recorder.response;

import java.util.List;

public class SaveLlmChatRecordResponse {

    private Long recordId;
    private String title;
    private List<String> labels;
    private String status;
    private String message;

    public SaveLlmChatRecordResponse() {
    }

    public SaveLlmChatRecordResponse(Long recordId, String title, List<String> labels) {
        this.recordId = recordId;
        this.title = title;
        this.labels = labels;
    }

    public SaveLlmChatRecordResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
