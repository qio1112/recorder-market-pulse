package com.yipeng.recorder.repository;

import com.yipeng.recorder.model.AlertSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AlertScheduleRepository extends JpaRepository<AlertSchedule, Long> {

    @Query("SELECT a FROM AlertSchedule a WHERE a.timeAt >= CURRENT_TIMESTAMP OR a.alertType = 'RECURRING'")
    List<AlertSchedule> findActiveSchedules();
}
