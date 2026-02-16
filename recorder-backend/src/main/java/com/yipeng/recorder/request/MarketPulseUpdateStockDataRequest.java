package com.yipeng.recorder.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MarketPulseUpdateStockDataRequest {
    @JsonProperty("update_previous_trade_date")
    private Boolean updatePreviousTradeDate = false;

    @JsonProperty("symbols")
    private List<String> symbols;

    @JsonProperty("option_symbols")
    private List<String> optionSymbols;

    @JsonProperty("symbols_path")
    private String symbolsPath;

    @JsonProperty("option_symbols_path")
    private String optionSymbolsPath;

    public MarketPulseUpdateStockDataRequest() {
    }

    public Boolean getUpdatePreviousTradeDate() {
        return updatePreviousTradeDate;
    }

    public void setUpdatePreviousTradeDate(Boolean updatePreviousTradeDate) {
        this.updatePreviousTradeDate = updatePreviousTradeDate;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }

    public List<String> getOptionSymbols() {
        return optionSymbols;
    }

    public void setOptionSymbols(List<String> optionSymbols) {
        this.optionSymbols = optionSymbols;
    }

    public String getSymbolsPath() {
        return symbolsPath;
    }

    public void setSymbolsPath(String symbolsPath) {
        this.symbolsPath = symbolsPath;
    }

    public String getOptionSymbolsPath() {
        return optionSymbolsPath;
    }

    public void setOptionSymbolsPath(String optionSymbolsPath) {
        this.optionSymbolsPath = optionSymbolsPath;
    }
}
