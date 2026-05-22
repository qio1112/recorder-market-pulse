package com.yipeng.recorder.service;

import com.yipeng.recorder.model.AlertExecution;
import com.yipeng.recorder.model.AlertSchedule;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.repository.AlertExecutionRepository;
import com.yipeng.recorder.repository.AlertScheduleRepository;
import com.yipeng.recorder.utils.AlertExecutionStatus;
import com.yipeng.recorder.utils.AlertType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

@Service
public class ScheduleAlertService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleAlertService.class);
    private static final int DEFAULT_ALERT_BATCH_SIZE = 50;

    private final SendEmailService sendEmailService;
    private final AlertScheduleRepository alertScheduleRepository;
    private final AlertExecutionRepository alertExecutionRepository;

    @Value("${alerts.scheduler.retry-delay-minutes:5}")
    private long retryDelayMinutes;

    @Value("${application.time-zone:America/New_York}")
    private String appTimeZone;

    public ScheduleAlertService(SendEmailService sendEmailService,
                                AlertScheduleRepository alertScheduleRepository,
                                AlertExecutionRepository alertExecutionRepository) {
        this.sendEmailService = sendEmailService;
        this.alertScheduleRepository = alertScheduleRepository;
        this.alertExecutionRepository = alertExecutionRepository;
    }

    @Transactional
    public void scheduleAlert(AlertSchedule schedule) {
        if (schedule == null) {
            return;
        }
        ZonedDateTime nextRunAt = computeNextRunAt(schedule, ZonedDateTime.now());
        schedule.setNextRunAt(nextRunAt);
        schedule.setEnabled(nextRunAt != null);
        schedule.setLastError(null);
        alertScheduleRepository.save(schedule);
        logger.info(
                "Saved alert schedule for record id={}, type={}, nextRunAt={}",
                schedule.getRecord().getId(),
                schedule.getAlertType(),
                nextRunAt
        );
    }

    @Transactional
    public void cancelAlertsForRecord(Long recordId) {
        alertScheduleRepository.findByRecordId(recordId).ifPresent(schedule -> {
            schedule.setEnabled(false);
            schedule.setNextRunAt(null);
            alertScheduleRepository.save(schedule);
            logger.info("Disabled alert schedule for record id={}", recordId);
        });
    }

    @Scheduled(fixedDelayString = "${alerts.scheduler.poll-delay-ms:5000}")
    public void processDueAlerts() {
        ZonedDateTime now = ZonedDateTime.now(appZone());
        refreshRecurringNextRunTimes(now);
        for (AlertSchedule schedule : alertScheduleRepository.findDueSchedules(now, PageRequest.of(0, DEFAULT_ALERT_BATCH_SIZE))) {
            try {
                sendDueAlert(schedule.getId(), now);
            } catch (Exception e) {
                logger.error("Failed to process alert schedule id={}", schedule.getId(), e);
            }
        }
    }

    @Transactional
    public void sendDueAlert(Long scheduleId, ZonedDateTime dueCheckTime) {
        AlertSchedule schedule = alertScheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null || !schedule.isEnabled() || schedule.getNextRunAt() == null
                || schedule.getNextRunAt().isAfter(dueCheckTime)) {
            return;
        }

        AlertExecution execution = createExecution(schedule);
        try {
            sendAlertEmail(schedule);
            ZonedDateTime finishedAt = ZonedDateTime.now();
            execution.setStatus(AlertExecutionStatus.SUCCESS);
            execution.setFinishedAt(finishedAt);
            alertExecutionRepository.save(execution);

            schedule.setLastSentAt(finishedAt);
            schedule.setLastError(null);
            if (schedule.getAlertType() == AlertType.ONE_TIME) {
                schedule.setEnabled(false);
                schedule.setNextRunAt(null);
            } else {
                schedule.setEnabled(true);
                schedule.setNextRunAt(computeNextRunAt(schedule, finishedAt.plusSeconds(1)));
            }
            alertScheduleRepository.save(schedule);
        } catch (Exception e) {
            ZonedDateTime finishedAt = ZonedDateTime.now();
            execution.setStatus(AlertExecutionStatus.FAILED);
            execution.setFinishedAt(finishedAt);
            execution.setErrorMessage(e.getMessage());
            alertExecutionRepository.save(execution);

            schedule.setLastError(e.getMessage());
            schedule.setNextRunAt(finishedAt.plusMinutes(Math.max(1, retryDelayMinutes)));
            alertScheduleRepository.save(schedule);
            throw new RuntimeException(e);
        }
    }

    ZonedDateTime computeNextRunAt(AlertSchedule schedule, ZonedDateTime now) {
        ZonedDateTime appNow = now.withZoneSameInstant(appZone());
        if (schedule.getAlertType() == AlertType.ONE_TIME) {
            ZonedDateTime alertTime = schedule.getTimeAt();
            return alertTime != null && alertTime.isAfter(now) ? alertTime.withZoneSameInstant(appZone()) : null;
        }
        if (schedule.getAlertType() != AlertType.RECURRING || StringUtils.isBlank(schedule.getWeekdays())) {
            return null;
        }

        return Arrays.stream(schedule.getWeekdays().split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(day -> computeNextRunForDay(day, schedule.getTimeAt(), appNow))
                .filter(Objects::nonNull)
                .min(ZonedDateTime::compareTo)
                .orElse(null);
    }

    private ZonedDateTime computeNextRunForDay(String weekday, ZonedDateTime atTimestamp, ZonedDateTime now) {
        DayOfWeek targetDay = DayOfWeek.valueOf(weekday.toUpperCase(Locale.ROOT));
        ZonedDateTime appAlertTime = atTimestamp == null ? null : atTimestamp.withZoneSameInstant(appZone());
        int daysUntilTarget = targetDay.getValue() - now.getDayOfWeek().getValue();
        if (daysUntilTarget < 0) {
            daysUntilTarget += 7;
        }
        ZonedDateTime targetTime = now.plusDays(daysUntilTarget)
                .withHour(appAlertTime == null ? 9 : appAlertTime.getHour())
                .withMinute(appAlertTime == null ? 0 : appAlertTime.getMinute())
                .withSecond(appAlertTime == null ? 0 : appAlertTime.getSecond())
                .withNano(0);
        if (!targetTime.isAfter(now)) {
            targetTime = targetTime.plusDays(7);
        }
        return targetTime;
    }

    private void refreshRecurringNextRunTimes(ZonedDateTime now) {
        for (AlertSchedule schedule : alertScheduleRepository.findEnabledRecurringSchedules()) {
            if (schedule.getNextRunAt() != null && !schedule.getNextRunAt().isAfter(now)) {
                continue;
            }
            ZonedDateTime nextRunAt = correctedRecurringNextRunAt(schedule, now);
            if (nextRunAt == null) {
                schedule.setEnabled(false);
                schedule.setNextRunAt(null);
                alertScheduleRepository.save(schedule);
                continue;
            }
            if (schedule.getNextRunAt() == null
                    || !schedule.getNextRunAt().toInstant().equals(nextRunAt.toInstant())) {
                schedule.setNextRunAt(nextRunAt);
                alertScheduleRepository.save(schedule);
            }
        }
    }

    private ZonedDateTime correctedRecurringNextRunAt(AlertSchedule schedule, ZonedDateTime now) {
        ZonedDateTime storedNextRunAt = schedule.getNextRunAt();
        ZonedDateTime alertClock = schedule.getTimeAt() == null
                ? null
                : schedule.getTimeAt().withZoneSameInstant(appZone());
        if (storedNextRunAt != null && alertClock != null) {
            ZonedDateTime storedLocal = storedNextRunAt.withZoneSameInstant(appZone());
            ZonedDateTime correctedForStoredDay = storedLocal
                    .withHour(alertClock.getHour())
                    .withMinute(alertClock.getMinute())
                    .withSecond(alertClock.getSecond())
                    .withNano(0);
            if (isSelectedWeekday(schedule, correctedForStoredDay.getDayOfWeek())
                    && (correctedForStoredDay.isAfter(now) || wasNotAlreadySent(schedule, correctedForStoredDay))) {
                return correctedForStoredDay;
            }
        }
        return computeNextRunAt(schedule, now);
    }

    private boolean isSelectedWeekday(AlertSchedule schedule, DayOfWeek dayOfWeek) {
        if (StringUtils.isBlank(schedule.getWeekdays())) {
            return false;
        }
        return Arrays.stream(schedule.getWeekdays().split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .anyMatch(day -> DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT)) == dayOfWeek);
    }

    private boolean wasNotAlreadySent(AlertSchedule schedule, ZonedDateTime intendedRunAt) {
        return schedule.getLastSentAt() == null || schedule.getLastSentAt().isBefore(intendedRunAt);
    }

    private ZoneId appZone() {
        return ZoneId.of(StringUtils.defaultIfBlank(appTimeZone, "America/New_York"));
    }

    private AlertExecution createExecution(AlertSchedule schedule) {
        AlertExecution execution = new AlertExecution();
        execution.setAlertScheduleId(schedule.getId());
        execution.setRecordId(schedule.getRecord().getId());
        execution.setRecipient(schedule.getRecord().getCreatedBy().getEmail());
        execution.setStatus(AlertExecutionStatus.RUNNING);
        execution.setStartedAt(ZonedDateTime.now());
        return alertExecutionRepository.save(execution);
    }

    private void sendAlertEmail(AlertSchedule schedule) throws Exception {
        Record record = schedule.getRecord();
        User user = record.getCreatedBy();
        if (user == null || StringUtils.isBlank(user.getEmail())) {
            throw new IllegalStateException("Alert recipient email is missing for record id=" + record.getId());
        }
        sendEmailService.sendEmail(user.getEmail(), "ALERT " + record.getTitle(), record.createEmailContent(), null);
        logger.info("Alert sent to {}, for record with id={}, title={}", user.getEmail(), record.getId(), record.getTitle());
    }
}
