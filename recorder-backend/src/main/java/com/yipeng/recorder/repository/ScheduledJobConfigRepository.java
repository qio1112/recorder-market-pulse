package com.yipeng.recorder.repository;

import com.yipeng.recorder.model.ScheduledJobConfig;
import com.yipeng.recorder.utils.JobScheduleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduledJobConfigRepository extends JpaRepository<ScheduledJobConfig, Long> {

    Optional<ScheduledJobConfig> findByJobKey(String jobKey);

    List<ScheduledJobConfig> findAllByOrderByJobKeyAsc();

    @Query("""
            select job from ScheduledJobConfig job
            where job.enabled = true
              and job.scheduleType <> :manualType
              and job.nextRunAt is not null
              and job.nextRunAt <= :now
            order by job.nextRunAt asc
            """)
    List<ScheduledJobConfig> findDueJobs(@Param("now") ZonedDateTime now,
                                         @Param("manualType") JobScheduleType manualType);
}
