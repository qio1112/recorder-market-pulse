package com.yipeng.recorder.response;

import com.yipeng.recorder.model.Record;

import java.util.List;

public class RelatedRecordResponse {

    private Record record;
    private Double score;
    private List<String> chunks;

    public RelatedRecordResponse() {
    }

    public RelatedRecordResponse(Record record, Double score, List<String> chunks) {
        this.record = record;
        this.score = score;
        this.chunks = chunks;
    }

    public Record getRecord() {
        return record;
    }

    public void setRecord(Record record) {
        this.record = record;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public List<String> getChunks() {
        return chunks;
    }

    public void setChunks(List<String> chunks) {
        this.chunks = chunks;
    }
}
