package com.yipeng.recorder.service;

import com.yipeng.recorder.model.RecFile;
import com.yipeng.recorder.repository.RecFileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public RecFile save(RecFile recFile) {
        return recFileRepository.save(recFile);
    }

    public List<RecFile> saveAll(List<RecFile> recFiles) {
        return recFileRepository.saveAll(recFiles);
    }

    public void deleteByIds(List<Long> ids) {
        recFileRepository.deleteByIds(ids);
    }
}
