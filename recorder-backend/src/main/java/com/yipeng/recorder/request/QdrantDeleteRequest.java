package com.yipeng.recorder.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QdrantDeleteRequest {
    @JsonProperty("record_id")
    private String recordId;
    @JsonProperty("collection")
    private String collection;

    public QdrantDeleteRequest() {
    }

    public QdrantDeleteRequest(String recordId, String collection) {
        this.recordId = recordId;
        this.collection = collection;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }
}
