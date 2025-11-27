package com.yipeng.recorder.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yipeng.recorder.utils.AlertType;
import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "alert_schedule")
public class AlertSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "record_id", nullable = false)
    private Record record;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType; // ONE_TIME or RECURRING

    @Column(name = "time_at")
    private ZonedDateTime timeAt; // the time of sending alerts, use the time for one-time directly, and use time part for recurring

    @Column(name = "weekdays")
    private String weekdays; // For recurring alerts, e.g., "MONDAY,TUESDAY,SATURDAY"

    @Column(name = "last_sent_at")
    private ZonedDateTime lastSentAt;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    public boolean isValid() {
        return alertType == AlertType.RECURRING || timeAt.isAfter(ZonedDateTime.now());
    }

    public AlertSchedule(Record record, AlertType alertType, ZonedDateTime timeAt, String weekdays) {
        this.record = record;
        this.alertType = alertType;
        this.timeAt = timeAt;
        this.weekdays = weekdays;
        this.createdAt = ZonedDateTime.now();
    }

    public AlertSchedule() {
        createdAt = ZonedDateTime.now();
    }

    @JsonProperty("record")
    public Map<String, String> getRecordCoreFields() {
        Map<String, String> recordCoreFields = new HashMap<>();
        recordCoreFields.put("title", record.getTitle());
        recordCoreFields.put("createdBy", record.getCreatedByUserId());
        return recordCoreFields;
    }

    public boolean isSameAlert(AlertSchedule otherAlertSchedule) {
        if (otherAlertSchedule == null) {
            return false;
        }
        if (id.equals(otherAlertSchedule.getId())) {
            return true;
        }
        return record.getId().equals(otherAlertSchedule.getRecord().getId())
                && (alertType == otherAlertSchedule.getAlertType() || alertType.equals(otherAlertSchedule.getAlertType()))
                && (timeAt == otherAlertSchedule.getTimeAt() || timeAt.equals(otherAlertSchedule.getTimeAt()))
                && (weekdays == otherAlertSchedule.getWeekdays() || weekdays.equals(otherAlertSchedule.getWeekdays()));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Record getRecord() {
        return record;
    }

    public void setRecord(Record record) {
        this.record = record;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public void setAlertType(AlertType alertType) {
        this.alertType = alertType;
    }

    public ZonedDateTime getTimeAt() {
        return timeAt;
    }

    public void setTimeAt(ZonedDateTime oneTimeAt) {
        this.timeAt = oneTimeAt;
    }

    public String getWeekdays() {
        return weekdays;
    }

    public void setWeekdays(String weekdays) {
        this.weekdays = weekdays;
    }

    public ZonedDateTime getLastSentAt() {
        return lastSentAt;
    }

    public void setLastSentAt(ZonedDateTime lastSentAt) {
        this.lastSentAt = lastSentAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }


}

