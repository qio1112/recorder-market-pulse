package com.yipeng.recorder.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yipeng.recorder.utils.RecFileType;
import jakarta.persistence.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;

@Entity
@Table(name = "rec_file")
public class RecFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_path")
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type")
    private RecFileType fileType;

    @Column(name = "creation_time")
    private ZonedDateTime creationTime;

    @ManyToOne
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    public RecFile() {}

    public RecFile(String fileName, RecFileType fileType, String filePath, User uploadedBy) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.filePath = filePath;
        this.uploadedBy = uploadedBy;
        this.creationTime = ZonedDateTime.now();
    }

    public Path getPath() {
        return Paths.get(this.filePath).resolve(this.fileName);
    }

    @JsonProperty("uploadedBy")
    public String getCreatedByUserId() {
        return uploadedBy.getUsername();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public ZonedDateTime getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(ZonedDateTime creationTime) {
        this.creationTime = creationTime;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(User uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public RecFileType getFileType() {
        return fileType;
    }

    public void setFileType(RecFileType fileType) {
        this.fileType = fileType;
    }
}
