package com.yipeng.recorder.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yipeng.recorder.model.StockDailyHistory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StockDailyHistoryResponse {

    @JsonProperty("Symbol")
    private String symbol;

    @JsonProperty("Datetime")
    private List<String> datetime = new ArrayList<>();

    @JsonProperty("High")
    private List<BigDecimal> high = new ArrayList<>();

    @JsonProperty("Low")
    private List<BigDecimal> low = new ArrayList<>();

    @JsonProperty("Open")
    private List<BigDecimal> open = new ArrayList<>();

    @JsonProperty("Close")
    private List<BigDecimal> close = new ArrayList<>();

    @JsonProperty("Volume")
    private List<Long> volume = new ArrayList<>();

    public StockDailyHistoryResponse(String symbol, List<String> datetime, List<BigDecimal> high, List<BigDecimal> low, List<BigDecimal> open, List<BigDecimal> close, List<Long> volume) {
        this.symbol = symbol;
        this.datetime = datetime;
        this.high = high;
        this.low = low;
        this.open = open;
        this.close = close;
        this.volume = volume;
    }

    public StockDailyHistoryResponse() {}

    public StockDailyHistoryResponse(StockDailyHistory stockHistory) {
        this.symbol = stockHistory.getSymbol();
        this.datetime.add(stockHistory.getTradeDate().toString());
        this.high.add(stockHistory.getHigh());
        this.low.add(stockHistory.getLow());
        this.open.add(stockHistory.getLow());
        this.close.add(stockHistory.getClose());
    }

    public StockDailyHistoryResponse(List<StockDailyHistory> stockHistory, String symbol) {
        this.symbol = symbol;
        stockHistory.forEach(sh -> {
            if (sh.getSymbol() != null && sh.getSymbol().equals(symbol)) {
                this.datetime.add(sh.getTradeDate().toString());
                this.high.add(sh.getHigh());
                this.low.add(sh.getLow());
                this.open.add(sh.getLow());
                this.close.add(sh.getClose());
            }
        });
    }

    public List<StockDailyHistory> convertToStockDailyHistory() {
        List<StockDailyHistory> result = new ArrayList<>();
        for (int i = 0; i < this.datetime.size(); i ++) {
            LocalDate date = LocalDate.parse(this.datetime.get(i));
            BigDecimal high = this.updateScaleToBigDecimal(this.high.get(i));
            BigDecimal low = this.updateScaleToBigDecimal(this.low.get(i));
            BigDecimal open = this.updateScaleToBigDecimal(this.open.get(i));
            BigDecimal close = this.updateScaleToBigDecimal(this.close.get(i));
            Long volume = this.volume.get(i);
            StockDailyHistory sh = new StockDailyHistory(this.symbol, date, high, low, open, close, volume);
            result.add(sh);
        }
        return result;
    }

    private BigDecimal updateScaleToBigDecimal(BigDecimal number) {
        return number == null ? null : number.setScale(4, RoundingMode.HALF_UP);
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public List<String> getDatetime() {
        return datetime;
    }

    public void setDatetime(List<String> datetime) {
        this.datetime = datetime;
    }

    public List<BigDecimal> getHigh() {
        return high;
    }

    public void setHigh(List<BigDecimal> high) {
        this.high = high;
    }

    public List<BigDecimal> getLow() {
        return low;
    }

    public void setLow(List<BigDecimal> low) {
        this.low = low;
    }

    public List<BigDecimal> getOpen() {
        return open;
    }

    public void setOpen(List<BigDecimal> open) {
        this.open = open;
    }

    public List<BigDecimal> getClose() {
        return close;
    }

    public void setClose(List<BigDecimal> close) {
        this.close = close;
    }

    public List<Long> getVolume() {
        return volume;
    }

    public void setVolume(List<Long> volume) {
        this.volume = volume;
    }
}
