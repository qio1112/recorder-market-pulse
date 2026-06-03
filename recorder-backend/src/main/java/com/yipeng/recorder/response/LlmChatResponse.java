package com.yipeng.recorder.response;

import java.util.ArrayList;
import java.util.List;

public class LlmChatResponse {

    private String reply;
    private List<String> toolUsages = new ArrayList<>();

    public LlmChatResponse() {
    }

    public LlmChatResponse(String reply) {
        this.reply = reply;
    }

    public LlmChatResponse(String reply, List<String> toolUsages) {
        this.reply = reply;
        this.toolUsages = toolUsages == null ? new ArrayList<>() : new ArrayList<>(toolUsages);
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<String> getToolUsages() {
        return toolUsages;
    }

    public void setToolUsages(List<String> toolUsages) {
        this.toolUsages = toolUsages == null ? new ArrayList<>() : new ArrayList<>(toolUsages);
    }
}
