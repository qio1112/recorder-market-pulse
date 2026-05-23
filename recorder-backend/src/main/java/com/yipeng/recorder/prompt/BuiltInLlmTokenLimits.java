package com.yipeng.recorder.prompt;

public final class BuiltInLlmTokenLimits {

    public static final int RECORD_LABEL_GENERATION_MAX_TOKENS = 700;
    public static final int CHAT_RECORD_TITLE_MAX_TOKENS = 300;
    public static final int CHAT_RECORD_SUMMARY_MAX_TOKENS = 2000;
    public static final int LLM_HEALTH_CHECK_MAX_TOKENS = 20;

    private BuiltInLlmTokenLimits() {
    }
}
