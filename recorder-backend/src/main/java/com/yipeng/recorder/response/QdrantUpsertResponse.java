package com.yipeng.recorder.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class QdrantUpsertResponse {
    @JsonProperty("point_ids")
    private List<String> pointIds;

    public List<String> getPointIds() {
        return pointIds;
    }
}
