package com.yipeng.recorder.response;

import java.util.List;

public class QdrantFilterResponse {
    private List<QdrantQueryResult> results;

    public QdrantFilterResponse(List<QdrantQueryResult> results) {
        this.results = results;
    }

    public List<QdrantQueryResult> getResults() {
        return results;
    }
}
