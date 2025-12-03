package com.yipeng.recorder.repository;

import com.yipeng.recorder.model.StockDailyHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface StockDailyHistoryRepository extends JpaRepository<StockDailyHistory, Long> {
    // Query by one symbol (sorted by date)
    List<StockDailyHistory> findBySymbolOrderByTradeDateAsc(String symbol);

    // Query by one symbol within date range (sorted)
    List<StockDailyHistory> findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
            String symbol,
            LocalDate start,
            LocalDate end
    );

    List<StockDailyHistory> findBySymbolInAndTradeDateGreaterThanEqualOrderBySymbolAscTradeDateAsc(
            List<String> symbols,
            LocalDate startDate
    );

    // Query by multiple symbols (all dates)
    List<StockDailyHistory> findBySymbolInOrderBySymbolAscTradeDateAsc(List<String> symbols);

    // Query by multiple symbols AND date range
    List<StockDailyHistory> findBySymbolInAndTradeDateBetweenOrderBySymbolAscTradeDateAsc(
            List<String> symbols,
            LocalDate start,
            LocalDate end
    );

    // Query by multiple symbols on certain date
    List<StockDailyHistory> findBySymbolInAndTradeDate(
            List<String> symbols,
            LocalDate tradeDate
    );

    // Get all existing symbols (distinct)
    @Query("SELECT DISTINCT s.symbol FROM StockDailyHistory s ORDER BY s.symbol")
    List<String> findAllSymbols();

    // Get earliest trade date for a symbol
    @Query("SELECT MIN(s.tradeDate) FROM StockDailyHistory s WHERE s.symbol = :symbol")
    LocalDate findEarliestDate(@Param("symbol") String symbol);

    // Get latest trade date for a symbol
    @Query("SELECT MAX(s.tradeDate) FROM StockDailyHistory s WHERE s.symbol = :symbol")
    LocalDate findLatestDate(@Param("symbol") String symbol);

    // find the most recent data of the symbol
    StockDailyHistory findTopBySymbolOrderByTradeDateDesc(String symbol);

    // fine the most recent two days of data of the symbol
    List<StockDailyHistory> findTop2BySymbolOrderByTradeDateDesc(String symbol);

    void deleteBySymbol(String symbol);

    int deleteBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    @Modifying
    @Query("DELETE FROM StockDailyHistory s WHERE s.symbol IN :symbols")
    int deleteBySymbols(@Param("symbols") List<String> symbols);
}
