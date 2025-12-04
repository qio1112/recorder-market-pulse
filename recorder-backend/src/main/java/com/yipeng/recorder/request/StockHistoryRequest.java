package com.yipeng.recorder.request;

import java.util.List;

public class StockHistoryRequest {

    List<String> symbols;

    public StockHistoryRequest() {
    }

    public StockHistoryRequest(List<String> symbols) {
        this.symbols = symbols;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }
}
