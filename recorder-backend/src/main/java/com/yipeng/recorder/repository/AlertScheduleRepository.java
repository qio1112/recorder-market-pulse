package com.yipeng.recorder.repository;

import com.yipeng.recorder.model.AlertSchedule;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface AlertScheduleRepository extends JpaRepository<AlertSchedule, Long> {

    @Query("SELECT a FROM AlertSchedule a WHERE a.timeAt >= CURRENT_TIMESTAMP OR a.alertType = 'RECURRING'")
    List<AlertSchedule> findActiveSchedules();

    Optional<AlertSchedule> findByRecordId(Long recordId);

    @Query("""
            SELECT a FROM AlertSchedule a
            JOIN FETCH a.record r
            JOIN FETCH r.createdBy
            WHERE a.enabled = true
              AND a.nextRunAt IS NOT NULL
              AND a.nextRunAt <= :now
            ORDER BY a.nextRunAt ASC
            """)
    List<AlertSchedule> findDueSchedules(@Param("now") ZonedDateTime now, Pageable pageable);

    @Query("""
            SELECT a FROM AlertSchedule a
            JOIN FETCH a.record r
            JOIN FETCH r.createdBy
            WHERE a.enabled = true
              AND a.alertType = com.yipeng.recorder.utils.AlertType.RECURRING
            """)
    List<AlertSchedule> findEnabledRecurringSchedules();

    @Query("""
            SELECT a FROM AlertSchedule a
            JOIN FETCH a.record r
            JOIN FETCH r.createdBy
            WHERE a.enabled = true
              AND a.nextRunAt IS NOT NULL
            ORDER BY a.nextRunAt ASC
            """)
    List<AlertSchedule> findActiveEnabledSchedules();

    @Query("""
            SELECT a FROM AlertSchedule a
            JOIN FETCH a.record r
            JOIN FETCH r.createdBy u
            WHERE a.enabled = true
              AND a.nextRunAt IS NOT NULL
              AND u.id = :userId
            ORDER BY a.nextRunAt ASC
            """)
    List<AlertSchedule> findActiveEnabledSchedulesForUser(@Param("userId") Long userId);

    @Query("""
            SELECT a FROM AlertSchedule a
            JOIN FETCH a.record r
            JOIN FETCH r.createdBy u
            WHERE (a.enabled = true AND a.nextRunAt IS NOT NULL)
               OR (a.alertType = com.yipeng.recorder.utils.AlertType.ONE_TIME AND a.timeAt > :now)
               OR (a.alertType = com.yipeng.recorder.utils.AlertType.RECURRING AND (a.enabled = true OR a.nextRunAt IS NOT NULL))
            ORDER BY a.nextRunAt ASC, a.timeAt ASC
            """)
    List<AlertSchedule> findDashboardSchedules(@Param("now") ZonedDateTime now);

    @Query("""
            SELECT a FROM AlertSchedule a
            JOIN FETCH a.record r
            JOIN FETCH r.createdBy u
            WHERE u.id = :userId
              AND (
                   (a.enabled = true AND a.nextRunAt IS NOT NULL)
                OR (a.alertType = com.yipeng.recorder.utils.AlertType.ONE_TIME AND a.timeAt > :now)
                OR (a.alertType = com.yipeng.recorder.utils.AlertType.RECURRING AND (a.enabled = true OR a.nextRunAt IS NOT NULL))
              )
            ORDER BY a.nextRunAt ASC, a.timeAt ASC
            """)
    List<AlertSchedule> findDashboardSchedulesForUser(@Param("userId") Long userId, @Param("now") ZonedDateTime now);
}
