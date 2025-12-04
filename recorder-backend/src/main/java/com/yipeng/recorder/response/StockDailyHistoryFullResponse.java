package com.yipeng.recorder.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class StockDailyHistoryFullResponse {

    @JsonProperty("InvalidSymbols")
    private List<String> invalidSymbols = new ArrayList<>();

    @JsonProperty("Data")
    private List<StockDailyHistoryForSymbolResponse> data = new ArrayList<>();

    public StockDailyHistoryFullResponse() {
    }

    public StockDailyHistoryFullResponse(List<String> invalidSymbols, List<StockDailyHistoryForSymbolResponse> data) {
        this.invalidSymbols = invalidSymbols;
        this.data = data;
    }

    public List<String> getInvalidSymbols() {
        return invalidSymbols;
    }

    public void setInvalidSymbols(List<String> invalidSymbols) {
        this.invalidSymbols = invalidSymbols;
    }

    public List<StockDailyHistoryForSymbolResponse> getData() {
        return data;
    }

    public void setData(List<StockDailyHistoryForSymbolResponse> data) {
        this.data = data;
    }
}
