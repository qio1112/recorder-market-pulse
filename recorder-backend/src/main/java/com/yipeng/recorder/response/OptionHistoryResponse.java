package com.yipeng.recorder.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class OptionHistoryResponse {

    private String symbol;
    private String expiry;

    @JsonProperty("option_type")
    private String optionType;

    private List<OptionStrikeHistoryResponse> strikes = new ArrayList<>();

    public OptionHistoryResponse() {
    }

    public OptionHistoryResponse(String symbol, String expiry, String optionType,
                                 List<OptionStrikeHistoryResponse> strikes) {
        this.symbol = symbol;
        this.expiry = expiry;
        this.optionType = optionType;
        this.strikes = strikes;
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

    public List<OptionStrikeHistoryResponse> getStrikes() {
        return strikes;
    }

    public void setStrikes(List<OptionStrikeHistoryResponse> strikes) {
        this.strikes = strikes;
    }
}
