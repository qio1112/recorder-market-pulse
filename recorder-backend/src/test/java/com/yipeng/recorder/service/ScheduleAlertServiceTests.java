package com.yipeng.recorder.service;

import com.yipeng.recorder.model.AlertSchedule;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.repository.AlertExecutionRepository;
import com.yipeng.recorder.repository.AlertScheduleRepository;
import com.yipeng.recorder.utils.AlertType;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleAlertServiceTests {

    @Test
    void oneTimeAlertUsesFutureAlertTimeAsNextRun() {
        ScheduleAlertService service = createService();
        ZonedDateTime now = ZonedDateTime.parse("2026-05-20T10:00:00-04:00[America/New_York]");
        ZonedDateTime alertTime = now.plusHours(2);
        AlertSchedule schedule = new AlertSchedule(record(), AlertType.ONE_TIME, alertTime, null);

        ZonedDateTime nextRunAt = service.computeNextRunAt(schedule, now);

        assertEquals(alertTime, nextRunAt);
    }

    @Test
    void recurringAlertChoosesNearestSelectedWeekdayTime() {
        ScheduleAlertService service = createService();
        ZonedDateTime now = ZonedDateTime.parse("2026-05-20T10:00:00-04:00[America/New_York]");
        ZonedDateTime alertTime = now.withHour(9).withMinute(30);
        AlertSchedule schedule = new AlertSchedule(record(), AlertType.RECURRING, alertTime, "FRIDAY,MONDAY");

        ZonedDateTime nextRunAt = service.computeNextRunAt(schedule, now);

        assertEquals(DayOfWeek.FRIDAY, nextRunAt.getDayOfWeek());
        assertEquals(9, nextRunAt.getHour());
        assertEquals(30, nextRunAt.getMinute());
        assertTrue(nextRunAt.isAfter(now));
    }

    @Test
    void recurringAlertUsesAppLocalClockWhenStoredAsUtc() {
        ScheduleAlertService service = createService();
        ZonedDateTime now = ZonedDateTime.parse("2026-05-22T14:00:00-04:00[America/New_York]");
        ZonedDateTime storedUtcAlertTime = ZonedDateTime.parse("2026-05-22T19:30:00Z");
        AlertSchedule schedule = new AlertSchedule(record(), AlertType.RECURRING, storedUtcAlertTime, "FRIDAY");

        ZonedDateTime nextRunAt = service.computeNextRunAt(schedule, now);

        assertEquals(DayOfWeek.FRIDAY, nextRunAt.getDayOfWeek());
        assertEquals(15, nextRunAt.getHour());
        assertEquals(30, nextRunAt.getMinute());
        assertEquals("America/New_York", nextRunAt.getZone().getId());
    }

    @Test
    void scheduleAlertPersistsEnabledScheduleWithNextRun() {
        AlertScheduleRepository alertScheduleRepository = mock(AlertScheduleRepository.class);
        when(alertScheduleRepository.save(any(AlertSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ScheduleAlertService service = new ScheduleAlertService(
                mock(SendEmailService.class),
                alertScheduleRepository,
                mock(AlertExecutionRepository.class)
        );
        AlertSchedule schedule = new AlertSchedule(record(), AlertType.ONE_TIME, ZonedDateTime.now().plusMinutes(30), null);

        service.scheduleAlert(schedule);

        assertTrue(schedule.isEnabled());
        assertNotNull(schedule.getNextRunAt());
        assertFalse(schedule.getNextRunAt().isBefore(ZonedDateTime.now()));
        verify(alertScheduleRepository).save(schedule);
    }

    private ScheduleAlertService createService() {
        return new ScheduleAlertService(
                mock(SendEmailService.class),
                mock(AlertScheduleRepository.class),
                mock(AlertExecutionRepository.class)
        );
    }

    private Record record() {
        User user = new User();
        user.setUsername("alert-user");
        user.setEmail("alert@example.com");
        return new Record("Alert Record", user, "Alert content", false);
    }
}
