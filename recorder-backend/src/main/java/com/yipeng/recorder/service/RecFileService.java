package com.yipeng.recorder.service;

import com.yipeng.recorder.model.RecFile;
import com.yipeng.recorder.repository.RecFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RecFileService {

    private final RecFileRepository recFileRepository;

    @Autowired
    public RecFileService(RecFileRepository recFileRepository) {
        this.recFileRepository = recFileRepository;
    }

    public RecFile findById(Long id) {
        return recFileRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public Boolean isRecFilePublic(Long recFileId) {
        // Use JOIN FETCH to eagerly load the record data
        RecFile recFile = recFileRepository.findByIdWithRecord(recFileId).orElse(null);
        if (recFile != null && recFile.getRecord() != null) {
            return recFile.getRecord().isPublic();
        }
        return false; // Return null if RecFile or Record not found
    }
}
