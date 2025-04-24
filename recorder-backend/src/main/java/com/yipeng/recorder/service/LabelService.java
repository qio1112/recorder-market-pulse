package com.yipeng.recorder.service;

import com.yipeng.recorder.model.Label;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.repository.LabelRepository;
import com.yipeng.recorder.utils.LabelType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LabelService {

    private final LabelRepository labelRepository;

    public LabelService(LabelRepository labelRepository) {
        this.labelRepository = labelRepository;
    }

    public List<Label> findAll() {
        return labelRepository.findAll();
    }

    public Boolean labelExists(String labelName) {
        return labelRepository.existsByLabelName(labelName);
    }
}
