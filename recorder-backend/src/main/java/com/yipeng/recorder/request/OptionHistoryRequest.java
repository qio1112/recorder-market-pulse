package com.yipeng.recorder.request;

public class OptionHistoryRequest {

    private String symbol;
    private String expiry;
    private String optionType;

    public OptionHistoryRequest() {
    }

    public OptionHistoryRequest(String symbol, String expiry, String optionType) {
        this.symbol = symbol;
        this.expiry = expiry;
        this.optionType = optionType;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getExpiry() {
        return expiry;
    }

    public void setExpiry(String expiry) {
        this.expiry = expiry;
    }

    public String getOptionType() {
        return optionType;
    }

    public void setOptionType(String optionType) {
        this.optionType = optionType;
    }
}
