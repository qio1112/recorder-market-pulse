package com.yipeng.recorder.response;

public class LlmChatResponse {

    private String reply;

    public LlmChatResponse() {
    }

    public LlmChatResponse(String reply) {
        this.reply = reply;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }
}
