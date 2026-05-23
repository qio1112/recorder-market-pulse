package com.yipeng.recorder.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class LlmChatRequest {

    private List<LlmChatMessage> messages = new ArrayList<>();
    private Double temperature;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    private Boolean includeRelatedRecords;
    private String chatMode;

    public List<LlmChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<LlmChatMessage> messages) {
        this.messages = messages;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Boolean getIncludeRelatedRecords() {
        return includeRelatedRecords;
    }

    public void setIncludeRelatedRecords(Boolean includeRelatedRecords) {
        this.includeRelatedRecords = includeRelatedRecords;
    }

    public String getChatMode() {
        return chatMode;
    }

    public void setChatMode(String chatMode) {
        this.chatMode = chatMode;
    }
}
