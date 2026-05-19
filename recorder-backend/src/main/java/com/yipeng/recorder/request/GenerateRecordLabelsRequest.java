package com.yipeng.recorder.request;

public class GenerateRecordLabelsRequest {

    private String title;
    private String content;
    private Integer maxLabels;

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

    public Integer getMaxLabels() {
        return maxLabels;
    }

    public void setMaxLabels(Integer maxLabels) {
        this.maxLabels = maxLabels;
    }
}
