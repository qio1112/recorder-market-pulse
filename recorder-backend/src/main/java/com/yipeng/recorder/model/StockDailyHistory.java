package com.yipeng.recorder.model;


import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
    name = "stock_daily_history",
    uniqueConstraints = {
            @UniqueConstraint(name = "uk_symbol_date", columnNames = {"symbol", "trade_date"})
    }
)
public class StockDailyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 16)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal open;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal high;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal low;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal close;

    @Column(nullable = false)
    private Long volume;

    public StockDailyHistory() {}

    public StockDailyHistory(String symbol, LocalDate tradeDate, BigDecimal high, BigDecimal low, BigDecimal open, BigDecimal close, Long volume) {
        this.symbol = symbol;
        this.tradeDate = tradeDate;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public boolean equalsByValue(StockDailyHistory other) {
        if (other == null) return false;

        return Objects.equals(this.symbol, other.symbol)
                && Objects.equals(this.tradeDate, other.tradeDate)
                && bdEqual(this.open, other.open)
                && bdEqual(this.high, other.high)
                && bdEqual(this.low, other.low)
                && bdEqual(this.close, other.close)
                && Objects.equals(this.volume, other.volume);
    }

    private boolean bdEqual(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }
}
