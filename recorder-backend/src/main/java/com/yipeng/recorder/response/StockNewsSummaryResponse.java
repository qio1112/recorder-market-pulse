package com.yipeng.recorder.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class StockNewsSummaryResponse {

    @JsonProperty("generated_at")
    private String generatedAt;
    private List<StockNewsSymbolSummaryResponse> summaries = new ArrayList<>();

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<StockNewsSymbolSummaryResponse> getSummaries() {
        return summaries;
    }

    public void setSummaries(List<StockNewsSymbolSummaryResponse> summaries) {
        this.summaries = summaries;
    }
}
