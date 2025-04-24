package com.yipeng.recorder.controller;

import com.yipeng.recorder.exception.ResourceNotFoundException;
import com.yipeng.recorder.model.RecFile;
import com.yipeng.recorder.service.RecFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/recfile/")
public class RecFileController {

    @Value("${recfile.upload.dir}")
    private String uploadDir;

    private final RecFileService recFileService;

    @Autowired
    public RecFileController(RecFileService recFileService) {
        this.recFileService = recFileService;
    }

    @GetMapping("/{fileID}")
    public ResponseEntity<Resource> serveFile(@PathVariable long fileID) {

        RecFile recFile = recFileService.findById(fileID);

        try {
            Path filePath = recFile.getPath();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(filePath))
                        .body(resource);
            } else {
                throw new ResourceNotFoundException("File not found: " + recFile.getFileName());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
