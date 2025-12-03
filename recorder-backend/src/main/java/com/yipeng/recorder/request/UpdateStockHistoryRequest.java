package com.yipeng.recorder.request;

import java.util.List;

public class UpdateStockHistoryRequest {

    List<String> symbols;

    public UpdateStockHistoryRequest() {
    }

    public UpdateStockHistoryRequest(List<String> symbols) {
        this.symbols = symbols;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }
}
