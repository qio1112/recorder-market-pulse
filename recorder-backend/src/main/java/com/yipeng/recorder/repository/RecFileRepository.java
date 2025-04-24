package com.yipeng.recorder.repository;

import com.yipeng.recorder.model.RecFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecFileRepository extends JpaRepository<RecFile, Long> {

    @Modifying
    @Query("DELETE FROM RecFile r WHERE r.id IN :ids")
    void deleteByIds(@Param("ids") List<Long> ids);
}
