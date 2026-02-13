package com.yipeng.recorder.response;

public class RecordDailyCountDto {

    private final String dateLabel;
    private final long recordCount;

    public RecordDailyCountDto(String dateLabel, long recordCount) {
        this.dateLabel = dateLabel;
        this.recordCount = recordCount;
    }

    public String getDateLabel() {
        return dateLabel;
    }

    public long getRecordCount() {
        return recordCount;
    }
}
