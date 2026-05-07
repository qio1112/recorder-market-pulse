package com.yipeng.recorder.response;

import java.util.ArrayList;
import java.util.List;

public class OptionSymbolsResponse {

    private List<String> symbols = new ArrayList<>();

    public OptionSymbolsResponse() {
    }

    public OptionSymbolsResponse(List<String> symbols) {
        this.symbols = symbols;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }
}
