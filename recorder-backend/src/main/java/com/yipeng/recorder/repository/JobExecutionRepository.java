package com.yipeng.recorder.repository;

import com.yipeng.recorder.model.JobExecution;
import com.yipeng.recorder.utils.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {

    Page<JobExecution> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select execution from JobExecution execution left join fetch execution.jobConfig where execution.id = :id")
    Optional<JobExecution> findByIdWithJobConfig(@Param("id") Long id);

    @Query("select execution from JobExecution execution left join fetch execution.jobConfig where execution.status in :statuses")
    List<JobExecution> findByStatusInWithJobConfig(@Param("statuses") Collection<JobStatus> statuses);

    @Query("""
            select execution from JobExecution execution
            left join fetch execution.jobConfig
            where execution.jobKey = :jobKey
              and execution.status in :statuses
            """)
    List<JobExecution> findByJobKeyAndStatusInWithJobConfig(@Param("jobKey") String jobKey,
                                                            @Param("statuses") Collection<JobStatus> statuses);

    boolean existsByJobKeyAndStatusIn(String jobKey, Collection<JobStatus> statuses);

    List<JobExecution> findTop20ByOrderByCreatedAtDesc();

    List<JobExecution> findTop50ByOrderByCreatedAtDesc();

    Optional<JobExecution> findFirstByJobKeyOrderByCreatedAtDesc(String jobKey);

    Optional<JobExecution> findFirstByJobTypeAndStatusOrderByFinishedAtDesc(String jobType, JobStatus status);

    Page<JobExecution> findByJobKeyInOrderByCreatedAtDesc(Collection<String> jobKeys, Pageable pageable);

    @Modifying
    @Transactional
    @Query("delete from JobExecution execution where execution.retentionUntil is not null and execution.retentionUntil < :now")
    int deleteExpired(@Param("now") ZonedDateTime now);
}
