package com.yipeng.recorder.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class OptionExpiryDatesResponse {

    private String symbol;

    @JsonProperty("expiry_dates")
    private List<OptionExpiryResponse> expiryDates = new ArrayList<>();

    public OptionExpiryDatesResponse() {
    }

    public OptionExpiryDatesResponse(String symbol, List<OptionExpiryResponse> expiryDates) {
        this.symbol = symbol;
        this.expiryDates = expiryDates;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public List<OptionExpiryResponse> getExpiryDates() {
        return expiryDates;
    }

    public void setExpiryDates(List<OptionExpiryResponse> expiryDates) {
        this.expiryDates = expiryDates;
    }
}
