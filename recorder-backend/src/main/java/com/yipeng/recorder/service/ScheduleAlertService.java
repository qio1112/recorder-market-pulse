package com.yipeng.recorder.service;

import com.yipeng.recorder.model.AlertSchedule;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.utils.AlertType;
import com.yipeng.recorder.utils.DateTimeUtils;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class ScheduleAlertService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleAlertService.class);

    private final SendEmailService sendEmailService;
    private final DateTimeUtils dateTimeUtils;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Autowired
    public ScheduleAlertService(SendEmailService sendEmailService, DateTimeUtils dateTimeUtils) {
        this.sendEmailService = sendEmailService;
        this.dateTimeUtils = dateTimeUtils;
    }

    public void scheduleAlert(AlertSchedule schedule) {
        if (schedule != null) {
            if (schedule.getAlertType() == AlertType.ONE_TIME) {
                scheduleOneTimeAlert(schedule);
            } else if (schedule.getAlertType() == AlertType.RECURRING) {
                scheduleRecurringAlert(schedule);
            }
        }
    }

    public void cancelAlertsForRecord(Long recordId) {
        ScheduledFuture<?> scheduledTask = scheduledTasks.remove(recordId);
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
            logger.info("Canceled alerts for record id={}", recordId);
        }
    }

    private void scheduleOneTimeAlert(AlertSchedule schedule) {
        ZonedDateTime alertTime = schedule.getTimeAt();
        long delay = alertTime.toInstant().toEpochMilli() - System.currentTimeMillis();

        if (delay > 0) {
            ScheduledFuture<?> task = scheduledExecutorService.schedule(() -> sendAlertEmail(schedule), delay, TimeUnit.MILLISECONDS);
            scheduledTasks.put(schedule.getRecord().getId(), task);
        }
        logger.info("One time alert scheduled for record id: {}, title: {}, at {}", schedule.getRecord().getId(), schedule.getRecord().getTitle(), alertTime);
    }

    private void scheduleRecurringAlert(AlertSchedule schedule) {
        String[] weekdays = schedule.getWeekdays().split(",");
        ZonedDateTime atTimestamp = schedule.getTimeAt();
        for (String day : weekdays) {
            // get the initial delayed time in milliseconds for each week day, then schedule recurring alerts for each week day
            long initialDelay = getInitialDelayForDay(day, atTimestamp);
            ScheduledFuture<?> task = scheduledExecutorService.scheduleAtFixedRate(
                    () -> sendAlertEmail(schedule),
                    initialDelay,
                    TimeUnit.DAYS.toMillis(7),
                    TimeUnit.MILLISECONDS
            );
            scheduledTasks.put(schedule.getRecord().getId(), task);
        }
        logger.info("Recurring alert scheduled for record id={}, title={}, on={}, at={}", schedule.getRecord().getId(), schedule.getRecord().getTitle(), weekdays, schedule.getTimeAt());
    }

    private void sendAlertEmail(AlertSchedule schedule) {
        String recipient = schedule.getRecord().getCreatedBy().getEmail();
        Record record = schedule.getRecord();
        User user = record.getCreatedBy();
        if (user != null && user.getEmail() != null) {
            try {
                sendEmailService.sendEmail(user.getEmail(), "ALERT " + record.getTitle(), record.createEmailContent(), null);
                logger.info("Alert sent to {}, for record with id={}, title={}", recipient, record.getId(), record.getTitle());
            } catch (Exception e) {
                logger.error("Failed to send alert email to {}, for record with id={}, title={}", recipient, record.getId(), record.getTitle());
                logger.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

    private long getInitialDelayForDay(String weekday, ZonedDateTime atTimestamp) {
        DayOfWeek targetDay = DayOfWeek.valueOf(weekday.toUpperCase(Locale.ROOT));
        ZonedDateTime currentDateTime = dateTimeUtils.getCurrentDateTime();
        DayOfWeek currentDayOfWeek = dateTimeUtils.getCurrentDayOfWeek();
        int daysUntilTarget = targetDay.getValue() - currentDayOfWeek.getValue();
        if (daysUntilTarget < 0) {
            daysUntilTarget += 7;
        }
        // default time is 9am
        ZonedDateTime targetTime = currentDateTime.plusDays(daysUntilTarget).withHour(9).withMinute(0).withSecond(0).withNano(0);
        if (atTimestamp != null) {
            targetTime = targetTime.withHour(atTimestamp.getHour()).withMinute(atTimestamp.getMinute()).withSecond(atTimestamp.getSecond()).withNano(atTimestamp.getNano());
        }
        return Duration.between(currentDateTime, targetTime).toMillis();
    }

    @PreDestroy
    public void shutdownScheduler() {
        scheduledExecutorService.shutdownNow();
    }
}
