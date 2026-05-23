package com.yipeng.recorder.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class QdrantRecordIdsResponse {

    @JsonProperty("record_ids")
    private List<String> recordIds;

    public List<String> getRecordIds() {
        return recordIds;
    }

    public void setRecordIds(List<String> recordIds) {
        this.recordIds = recordIds;
    }
}
