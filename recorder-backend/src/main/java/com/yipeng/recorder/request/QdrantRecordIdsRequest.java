package com.yipeng.recorder.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QdrantRecordIdsRequest {

    @JsonProperty("collection")
    private String collection;
    @JsonProperty("page_size")
    private Integer pageSize;

    public QdrantRecordIdsRequest(String collection, Integer pageSize) {
        this.collection = collection;
        this.pageSize = pageSize;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
