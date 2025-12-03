package com.yipeng.recorder.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class DateTimeUtils {

    @Value("${application.time-zone}")
    private String appTimeZone;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    public DateTimeUtils() {

    }

    public DateTimeFormatter getTimestampFormatter() {
        return TIMESTAMP_FORMATTER;
    }

    public DateTimeFormatter getDateFormatter() {
        return DATE_FORMATTER;
    }

    public ZonedDateTime getCurrentDateTime() {
        return ZonedDateTime.now(ZoneId.of(appTimeZone));
    }

    public ZonedDateTime getCurrentDateTimeWithDelay(long secondDelay) {
        return ZonedDateTime.now(ZoneId.of(appTimeZone)).plusSeconds(secondDelay);
    }

    public String getCurrentDateString() {
        ZonedDateTime currentDateTime = ZonedDateTime.now(ZoneId.of(appTimeZone));
        return currentDateTime.toLocalDate().format(DATE_FORMATTER);
    }

    public boolean isValidDateString(String dateString) {
        try {
            LocalDate.parse(dateString, DATE_FORMATTER);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public String addDaysToDateString(String dateString, int daysToAdd) {
        if (!isValidDateString(dateString)) {
            throw new IllegalArgumentException("Invalid date string: " + dateString);
        }
        LocalDate date = LocalDate.parse(dateString, DATE_FORMATTER);
        LocalDate newDate = date.plusDays(daysToAdd);
        return newDate.format(DATE_FORMATTER);
    }

    public String convertLocalDateToString(LocalDate localDate) {
        return localDate.format(DATE_FORMATTER);
    }

    public LocalDate getLocalDateFromString(String dateString) {
        return LocalDate.parse(dateString);
    }

    public ZonedDateTime getZonedDateTimeFromString(String dateString, boolean startOfDay) {
        if (!isValidDateString(dateString)) {
            throw new IllegalArgumentException("Invalid date string: " + dateString);
        }
        LocalDate date = LocalDate.parse(dateString);
        LocalDateTime datetime = startOfDay ? date.atStartOfDay() : date.atTime(23, 59, 59); // Add 00:00:00 time
        return datetime.atZone(ZoneId.of(appTimeZone)); // Convert to ZonedDateTime in New York
    }

    public DayOfWeek getCurrentDayOfWeek() {
        ZonedDateTime currentDateTime = ZonedDateTime.now(ZoneId.of(appTimeZone));
        return currentDateTime.getDayOfWeek();
    }
}
