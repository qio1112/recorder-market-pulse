package com.yipeng.recorder.request;

import java.time.LocalDate;
import java.util.List;

public class ListRecordsRequest {

    private List<String> labels;
    private List<String> excludeLabels;
    private String titleContains;
    private LocalDate creationAfterDate;
    private LocalDate creationBeforeDate;
    private LocalDate modifiedAfterDate;
    private LocalDate modifiedBeforeDate;
    private Boolean isPublic;
    private Boolean isCreatedByUserOnly;
    private Integer pageSize;
    private Integer page;
    private String sortBy = "";

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public List<String> getExcludeLabels() {
        return excludeLabels;
    }

    public void setExcludeLabels(List<String> excludeLabels) {
        this.excludeLabels = excludeLabels;
    }

    public String getTitleContains() {
        return titleContains;
    }

    public void setTitleContains(String titleContains) {
        this.titleContains = titleContains;
    }

    public LocalDate getCreationAfterDate() {
        return creationAfterDate;
    }

    public void setCreationAfterDate(LocalDate creationAfterDate) {
        this.creationAfterDate = creationAfterDate;
    }

    public LocalDate getCreationBeforeDate() {
        return creationBeforeDate;
    }

    public void setCreationBeforeDate(LocalDate creationBeforeDate) {
        this.creationBeforeDate = creationBeforeDate;
    }

    public Boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean aPublic) {
        isPublic = aPublic;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public LocalDate getModifiedAfterDate() {
        return modifiedAfterDate;
    }

    public void setModifiedAfterDate(LocalDate modifiedAfterDate) {
        this.modifiedAfterDate = modifiedAfterDate;
    }

    public LocalDate getModifiedBeforeDate() {
        return modifiedBeforeDate;
    }

    public void setModifiedBeforeDate(LocalDate modifiedBeforeDate) {
        this.modifiedBeforeDate = modifiedBeforeDate;
    }

    public Boolean getIsCreatedByUserOnly() {
        return isCreatedByUserOnly;
    }

    public void setIsCreatedByUserOnly(Boolean isCreatedByUserOnly) {
        this.isCreatedByUserOnly = isCreatedByUserOnly;
    }
}
