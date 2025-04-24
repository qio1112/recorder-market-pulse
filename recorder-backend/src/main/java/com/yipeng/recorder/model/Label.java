package com.yipeng.recorder.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yipeng.recorder.utils.LabelType;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "labels")
public class Label {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label_name", nullable = false, unique = true)
    private String labelName;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy; // The user who created the label (can be null)

    @Column(name = "creation_time", nullable = false)
    private ZonedDateTime creationTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private LabelType type;

    public Label() {}

    public Label(String labelName, User createdBy, LabelType type) {
        this.labelName = labelName;
        this.createdBy = createdBy;
        this.creationTime = ZonedDateTime.now();
        this.type = type;
    }

    @JsonProperty("createdBy")
    public String getCreatedByUserId() {
        return createdBy.getUsername();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabelName() {
        return labelName;
    }

    public void setLabelName(String labelName) {
        this.labelName = labelName;
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

    public LabelType getType() {
        return type;
    }

    public void setType(LabelType type) {
        this.type = type;
    }
}
