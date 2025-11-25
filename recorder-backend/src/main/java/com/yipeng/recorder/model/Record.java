package com.yipeng.recorder.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "records")
public class Record {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "creation_time", nullable = false)
    private ZonedDateTime creationTime;

    @Column(name = "last_modified_time")
    private ZonedDateTime lastModifiedTime;

    @Column(nullable = false)
    private String content;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "record_label",
            joinColumns = @JoinColumn(name = "record_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id")
    )
    private List<Label> labels = new ArrayList<>(); // Many-to-many relationship with labels

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "record_id")
    private List<RecFile> recFiles = new ArrayList<>();

    @Column(name="is_public")
    private boolean isPublic;

    @OneToOne(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    private AlertSchedule alertSchedule;

    public Record() {
        this.creationTime = ZonedDateTime.now();
        this.lastModifiedTime = this.creationTime;
    }

    public Record(String title, User createdBy, String content, boolean isPublic) {
        this.title = title;
        this.createdBy = createdBy;
        this.content = content;
        this.creationTime = ZonedDateTime.now();
        this.lastModifiedTime = this.creationTime;
        this.isPublic = isPublic;
    }

    @JsonProperty("createdBy")
    public String getCreatedByUserId() {
        return createdBy.getUsername();
    }

    @JsonProperty("labels")
    public List<Map<String, String>> getLabelsCoreFields() {
        return this.labels.stream()
                .map(label -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("labelName", label.getLabelName());
                    map.put("type", label.getType().name());
                    return map;
                })
                .toList();
    }

    @JsonProperty("recFiles")
    public List<Map<String, String>> getRecFilesCoreFields() {
        return this.recFiles.stream()
                .map(file -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("fileID", String.valueOf(file.getId()));
                    map.put("filename", file.getFileName());
                    map.put("fileType", file.getFileType().name());
                    return map;
                })
                .toList();
    }

    public String createEmailContent() {
        StringBuffer sb = new StringBuffer();
        sb.append("Alert sending to ").append(createdBy.getUsername()).append(": \n\n");
        sb.append(content);
        return sb.toString();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public ZonedDateTime getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(ZonedDateTime creationTime) {
        this.creationTime = creationTime;
    }

    public ZonedDateTime getLastModifiedTime() {
        return lastModifiedTime;
    }

    public void setLastModifiedTime(ZonedDateTime lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Label> getLabels() {
        return labels;
    }

    public void setLabels(List<Label> labels) {
        this.labels = labels;
    }

    public List<RecFile> getRecFiles() {
        return recFiles;
    }

    public void setRecFiles(List<RecFile> recFiles) {
        this.recFiles = recFiles;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public AlertSchedule getAlertSchedule() {
        return alertSchedule;
    }

    public void setAlertSchedule(AlertSchedule alertSchedules) {
        this.alertSchedule = alertSchedules;
    }
}
