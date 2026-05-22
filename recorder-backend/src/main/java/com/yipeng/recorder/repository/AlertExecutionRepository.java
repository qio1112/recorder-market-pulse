package com.yipeng.recorder.repository;

import com.yipeng.recorder.model.AlertExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertExecutionRepository extends JpaRepository<AlertExecution, Long> {
}
