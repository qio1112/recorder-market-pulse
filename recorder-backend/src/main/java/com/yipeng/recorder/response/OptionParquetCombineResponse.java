package com.yipeng.recorder.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OptionParquetCombineResponse {

    @JsonProperty("combined_count")
    private Integer combinedCount;

    public OptionParquetCombineResponse() {
    }

    public OptionParquetCombineResponse(Integer combinedCount) {
        this.combinedCount = combinedCount;
    }

    public Integer getCombinedCount() {
        return combinedCount;
    }

    public void setCombinedCount(Integer combinedCount) {
        this.combinedCount = combinedCount;
    }
}
