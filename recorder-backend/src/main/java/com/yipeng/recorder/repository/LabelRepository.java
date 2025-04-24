package com.yipeng.recorder.repository;

import com.yipeng.recorder.model.Label;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LabelRepository extends JpaRepository<Label, Long> {

    boolean existsByLabelName(String labelName);

    Optional<Label> findByLabelName(String labelName);

    Set<Label> findByLabelNameContainingIgnoreCase(String labelNameSubstring);

    Set<Label> findByType(String type);

    @Query("SELECT l FROM Record r JOIN r.labels l WHERE r.id = :recordId")
    Set<Label> findByRecordId(@Param("recordId") Long recordId);

    // Find all labels where the name matches one in the given list
    List<Label> findAllByLabelNameIn(List<String> labelNames);
}
