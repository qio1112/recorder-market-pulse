package com.yipeng.recorder.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class QdrantUpsertRequest {
    @JsonProperty("record_id")
    private String recordId;
    @JsonProperty("owner_user_id")
    private String ownerUserId;
    @JsonProperty("is_public")
    private boolean isPublic;
    @JsonProperty("text")
    private String text;
    @JsonProperty("labels")
    private List<String> labels;
    @JsonProperty("collection")
    private String collection;

    public QdrantUpsertRequest() {
    }

    public QdrantUpsertRequest(String recordId, String ownerUserId, boolean isPublic, String text, List<String> labels, String collection) {
        this.recordId = recordId;
        this.ownerUserId = ownerUserId;
        this.isPublic = isPublic;
        this.text = text;
        this.labels = labels != null ? labels : new ArrayList<>();
        this.collection = collection;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }
}
