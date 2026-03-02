package com.yipeng.recorder.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QdrantQueryRequest {
    @JsonProperty("user_id")
    private String userId;
    @JsonProperty("query_text")
    private String queryText;
    @JsonProperty("similarity_threshold")
    private Double similarityThreshold;
    @JsonProperty("limit")
    private Integer limit;
    @JsonProperty("collection")
    private String collection;

    public QdrantQueryRequest() {
    }

    public QdrantQueryRequest(String userId, String queryText, Double similarityThreshold, Integer limit, String collection) {
        this.userId = userId;
        this.queryText = queryText;
        this.similarityThreshold = similarityThreshold;
        this.limit = limit;
        this.collection = collection;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public Double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(Double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }
}
