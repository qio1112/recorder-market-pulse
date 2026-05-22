package com.yipeng.recorder.response;

import com.yipeng.recorder.model.AlertSchedule;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.utils.AlertType;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public class AlertScheduleResponse {

    private Long id;
    private Long recordId;
    private String recordTitle;
    private String authorUsername;
    private AlertType alertType;
    private ZonedDateTime timeAt;
    private String weekdays;
    private ZonedDateTime nextRunAt;
    private ZonedDateTime lastSentAt;
    private boolean enabled;

    public static AlertScheduleResponse from(AlertSchedule schedule) {
        return from(schedule, ZoneId.systemDefault());
    }

    public static AlertScheduleResponse from(AlertSchedule schedule, ZoneId zoneId) {
        AlertScheduleResponse response = new AlertScheduleResponse();
        Record record = schedule.getRecord();
        User author = record == null ? null : record.getCreatedBy();
        response.id = schedule.getId();
        response.recordId = record == null ? null : record.getId();
        response.recordTitle = record == null ? "" : record.getTitle();
        response.authorUsername = author == null ? "" : author.getUsername();
        response.alertType = schedule.getAlertType();
        response.timeAt = toZone(schedule.getTimeAt(), zoneId);
        response.weekdays = schedule.getWeekdays();
        response.nextRunAt = toZone(schedule.getNextRunAt(), zoneId);
        response.lastSentAt = toZone(schedule.getLastSentAt(), zoneId);
        response.enabled = schedule.isEnabled();
        return response;
    }

    private static ZonedDateTime toZone(ZonedDateTime value, ZoneId zoneId) {
        return value == null ? null : value.withZoneSameInstant(zoneId);
    }

    public Long getId() { return id; }
    public Long getRecordId() { return recordId; }
    public String getRecordTitle() { return recordTitle; }
    public String getAuthorUsername() { return authorUsername; }
    public AlertType getAlertType() { return alertType; }
    public ZonedDateTime getTimeAt() { return timeAt; }
    public String getWeekdays() { return weekdays; }
    public ZonedDateTime getNextRunAt() { return nextRunAt; }
    public ZonedDateTime getLastSentAt() { return lastSentAt; }
    public boolean isEnabled() { return enabled; }
}
