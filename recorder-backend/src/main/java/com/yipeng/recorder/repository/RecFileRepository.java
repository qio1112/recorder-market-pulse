package com.yipeng.recorder.repository;

import com.yipeng.recorder.model.RecFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecFileRepository extends JpaRepository<RecFile, Long> {

    @Modifying
    @Query("DELETE FROM RecFile r WHERE r.id IN :ids")
    void deleteByIds(@Param("ids") List<Long> ids);

    @Query("SELECT rf FROM RecFile rf JOIN FETCH rf.record WHERE rf.id = :id")
    Optional<RecFile> findByIdWithRecord(@Param("id") Long id);

    @Query("SELECT rf FROM RecFile rf JOIN FETCH rf.record WHERE rf.id IN :ids")
    List<RecFile> findByIdsWithRecord(@Param("ids") List<Long> ids);
}
