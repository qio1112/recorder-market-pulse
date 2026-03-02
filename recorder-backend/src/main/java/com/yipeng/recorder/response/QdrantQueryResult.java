package com.yipeng.recorder.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class QdrantQueryResult {
    @JsonProperty("record_id")
    private String recordId;
    @JsonProperty("owner_user_id")
    private String ownerUserId;
    @JsonProperty("is_public")
    private Boolean isPublic;
    private List<String> chunks;
    @JsonProperty("best_score")
    private Double bestScore;

    public String getRecordId() {
        return recordId;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public List<String> getChunks() {
        return chunks;
    }

    public Double getBestScore() {
        return bestScore;
    }
}
