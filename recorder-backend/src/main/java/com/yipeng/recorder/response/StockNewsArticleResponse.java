package com.yipeng.recorder.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StockNewsArticleResponse {

    private String title;
    private String publisher;
    private String link;
    @JsonProperty("publish_time")
    private Object publishTime;
    private String summary;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Object getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(Object publishTime) {
        this.publishTime = publishTime;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
