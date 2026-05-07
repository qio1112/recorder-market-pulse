package com.yipeng.recorder.request;

public class OptionExpiryDatesRequest {

    private String symbol;

    public OptionExpiryDatesRequest() {
    }

    public OptionExpiryDatesRequest(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}
