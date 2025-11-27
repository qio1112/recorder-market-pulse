package com.yipeng.recorder.repository;

import com.yipeng.recorder.model.Label;
import com.yipeng.recorder.model.Record;
import com.yipeng.recorder.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

public interface RecordRepository extends JpaRepository<Record, Long> {

    List<Record> findByTitle(String title);

    List<Record> findByTitleContainingIgnoreCase(String title);

    List<Record> findByCreatedBy(User user);

    Page<Record> findByCreatedBy(User user, Pageable pageable);

    List<Record> findByCreationTimeAfter(ZonedDateTime time);

    Page<Record> findByCreationTimeAfter(ZonedDateTime time, Pageable pageable);

    @Query("SELECT r FROM Record r WHERE r.createdBy = :user OR r.isPublic = true")
    List<Record> findByCreatedByOrIsPublic(@Param("user") User user);

    @Query("SELECT r FROM Record r WHERE r.createdBy = :user OR r.isPublic = true")
    Page<Record> findByCreatedByOrIsPublic(@Param("user") User user, Pageable pageable);

    @Query("""
            SELECT r FROM Record r JOIN r.labels l
            WHERE l IN :labels
            GROUP BY r.id
            HAVING COUNT(DISTINCT l) = :labelCount""")
    Page<Record> findByLabelsContainingAll(@Param("labels") List<Label> labels,
                                           @Param("labelCount") long labelCount,
                                           Pageable pageable);

    @Query("""
            SELECT r FROM Record r JOIN r.labels l
            WHERE l IN :labels AND LOWER(r.title) LIKE LOWER(CONCAT('%', :title, '%'))
            GROUP BY r.id
            HAVING COUNT(DISTINCT l) = :labelCount""")
    Page<Record> findByLabelsContainingAllAndTitleContainingIgnoreCase(@Param("labels") List<Label> labels,
                                                                       @Param("labelCount") long labelCount,
                                                                       @Param("title") String title,
                                                                       Pageable pageable);

    @Query("""
    SELECT DISTINCT r FROM Record r
    LEFT JOIN r.createdBy
    LEFT JOIN r.labels
    WHERE (:labels IS NULL OR (SELECT COUNT(label) FROM r.labels label WHERE label.labelName IN :labels) = :labelCount)
      AND (:title IS NULL OR LOWER(r.title) LIKE LOWER(CONCAT('%', :title, '%')))
      AND (:creationAfterDate IS NULL OR r.creationTime >= :creationAfterDate)
      AND (:creationBeforeDate IS NULL OR r.creationTime <= :creationBeforeDate)
      AND (:modifiedAfterDate is NULL OR r.lastModifiedTime >= :modifiedAfterDate)
      AND (:modifiedBeforeDate is NULL OR r.lastModifiedTime <= :modifiedBeforeDate)
      AND (:isPublic IS NULL OR r.isPublic = :isPublic)
      AND (:userIsAdmin = true OR r.createdBy.id = :userId OR r.isPublic = true)
      AND (:isCreatedByUserOnly = false OR (:isCreatedByUserOnly = true AND r.createdBy.id = :userId))
    """)
    Page<Record> filterRecords(
            @Param("labels") List<String> labels,
            @Param("labelCount") Long labelCount,
            @Param("title") String title,
            @Param("creationAfterDate") ZonedDateTime creationAfterDate,
            @Param("creationBeforeDate") ZonedDateTime creationBeforeDate,
            @Param("modifiedAfterDate") ZonedDateTime modifiedAfterDate,
            @Param("modifiedBeforeDate") ZonedDateTime modifiedBeforeDate,
            @Param("isPublic") Boolean isPublic,
            @Param("isCreatedByUserOnly") Boolean isCreatedByUserOnly,
            @Param("userId") Long userId,
            @Param("userIsAdmin") Boolean isAdmin,
            Pageable pageable
    );

}
