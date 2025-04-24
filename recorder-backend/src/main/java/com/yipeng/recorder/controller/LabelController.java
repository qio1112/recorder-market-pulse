package com.yipeng.recorder.controller;

import com.yipeng.recorder.model.Label;
import com.yipeng.recorder.service.LabelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/labels")
public class LabelController {

    private final LabelService labelService;

    @Autowired
    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @GetMapping(value = "/all-labels")
    public ResponseEntity<List<Label>> getAllLabels() {
        List<Label> allLabels = labelService.findAll();
        return ResponseEntity.ok(allLabels);
    }

    @GetMapping(value = "/label-exists/{label}")
    public ResponseEntity<Boolean> labelExists(@PathVariable("label")String label) {
        Boolean labelExists = labelService.labelExists(label);
        return ResponseEntity.ok(labelExists);
    }
}
