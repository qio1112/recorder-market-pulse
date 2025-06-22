package com.yipeng.stockapi.controller;

import com.yipeng.stockapi.model.StockData;
import com.yipeng.stockapi.service.MockStockDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StockApiController {

    private final MockStockDataService mockStockDataService;

    @GetMapping("/price/{symbol}")
    public ResponseEntity<StockData> getStockPrice(@PathVariable("symbol") String symbol) {
        StockData stockData = mockStockDataService.generateMockStockData(symbol);
        return ResponseEntity.ok(stockData);
    }

    @GetMapping("/prices")
    public ResponseEntity<List<StockData>> getMultipleStockPrices(@RequestParam("symbols") List<String> symbols) {
        List<StockData> stockDataList = mockStockDataService.generateMockStockDataForSymbols(symbols);
        return ResponseEntity.ok(stockDataList);
    }

    @GetMapping("/all")
    public ResponseEntity<List<StockData>> getAllStockPrices() {
        List<StockData> allStockData = mockStockDataService.generateAllMockStockData();
        return ResponseEntity.ok(allStockData);
    }

    @GetMapping("/market-summary")
    public ResponseEntity<Map<String, Object>> getMarketSummary() {
        Map<String, Object> marketSummary = mockStockDataService.generateMarketSummary();
        return ResponseEntity.ok(marketSummary);
    }

    @GetMapping("/historical/{symbol}")
    public ResponseEntity<List<StockData>> getHistoricalData(
            @PathVariable("symbol") String symbol,
            @RequestParam(value = "days", defaultValue = "30") int days) {
        List<StockData> historicalData = mockStockDataService.generateHistoricalData(symbol, days);
        return ResponseEntity.ok(historicalData);
    }
} 