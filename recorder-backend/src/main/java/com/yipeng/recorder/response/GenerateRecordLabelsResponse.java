package com.yipeng.recorder.response;

import java.util.List;

public class GenerateRecordLabelsResponse {

    private List<String> labels;

    public GenerateRecordLabelsResponse() {
    }

    public GenerateRecordLabelsResponse(List<String> labels) {
        this.labels = labels;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }
}
