package com.yipeng.recorder.model;

import com.yipeng.recorder.utils.AlertExecutionStatus;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "alert_execution",
        indexes = {
                @Index(name = "idx_alert_execution_schedule", columnList = "alert_schedule_id"),
                @Index(name = "idx_alert_execution_record", columnList = "record_id"),
                @Index(name = "idx_alert_execution_started", columnList = "started_at")
        }
)
public class AlertExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_schedule_id", nullable = false)
    private Long alertScheduleId;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "recipient")
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AlertExecutionStatus status;

    @Column(name = "started_at", nullable = false)
    private ZonedDateTime startedAt;

    @Column(name = "finished_at")
    private ZonedDateTime finishedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public Long getAlertScheduleId() {
        return alertScheduleId;
    }

    public void setAlertScheduleId(Long alertScheduleId) {
        this.alertScheduleId = alertScheduleId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public AlertExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(AlertExecutionStatus status) {
        this.status = status;
    }

    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(ZonedDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public ZonedDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(ZonedDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
