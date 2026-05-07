package com.yipeng.recorder.response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OptionStrikeHistoryResponse {

    private Double strike;
    private Object contractSymbol;
    private Object contractSize;
    private Map<String, List<Object>> history = new HashMap<>();

    public OptionStrikeHistoryResponse() {
    }

    public OptionStrikeHistoryResponse(Double strike, Object contractSymbol, Object contractSize,
                                       Map<String, List<Object>> history) {
        this.strike = strike;
        this.contractSymbol = contractSymbol;
        this.contractSize = contractSize;
        this.history = history;
    }

    public Double getStrike() {
        return strike;
    }

    public void setStrike(Double strike) {
        this.strike = strike;
    }

    public Object getContractSymbol() {
        return contractSymbol;
    }

    public void setContractSymbol(Object contractSymbol) {
        this.contractSymbol = contractSymbol;
    }

    public Object getContractSize() {
        return contractSize;
    }

    public void setContractSize(Object contractSize) {
        this.contractSize = contractSize;
    }

    public Map<String, List<Object>> getHistory() {
        return history;
    }

    public void setHistory(Map<String, List<Object>> history) {
        this.history = history;
    }
}
