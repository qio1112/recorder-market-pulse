package com.yipeng.recorder.request;

import java.util.ArrayList;
import java.util.List;

public class SaveLlmChatRecordRequest {

    private List<LlmChatMessage> messages = new ArrayList<>();
    private boolean isPublic;

    public List<LlmChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<LlmChatMessage> messages) {
        this.messages = messages;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }
}
