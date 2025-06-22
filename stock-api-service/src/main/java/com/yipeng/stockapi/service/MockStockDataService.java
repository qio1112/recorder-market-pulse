package com.yipeng.stockapi.service;

import com.yipeng.stockapi.model.StockData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MockStockDataService {

    private final Map<String, BigDecimal> basePrices = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public MockStockDataService() {
        // Initialize base prices for popular stocks
        basePrices.put("AAPL", new BigDecimal("150.00"));
        basePrices.put("GOOGL", new BigDecimal("2800.00"));
        basePrices.put("MSFT", new BigDecimal("300.00"));
        basePrices.put("AMZN", new BigDecimal("3300.00"));
        basePrices.put("TSLA", new BigDecimal("800.00"));
        basePrices.put("NVDA", new BigDecimal("500.00"));
        basePrices.put("META", new BigDecimal("350.00"));
        basePrices.put("NFLX", new BigDecimal("600.00"));
        basePrices.put("SPY", new BigDecimal("450.00"));
        basePrices.put("QQQ", new BigDecimal("380.00"));
    }

    public StockData generateMockStockData(String symbol) {
        BigDecimal basePrice = basePrices.getOrDefault(symbol.toUpperCase(), new BigDecimal("100.00"));
        
        // Generate realistic price movement (±5% range)
        double changePercent = (random.nextDouble() - 0.5) * 0.1; // ±5%
        BigDecimal price = basePrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(changePercent)))
                .setScale(2, RoundingMode.HALF_UP);
        
        BigDecimal change = price.subtract(basePrice);
        BigDecimal changePercentBD = change.divide(basePrice, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        
        // Generate OHLC data
        BigDecimal open = basePrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf((random.nextDouble() - 0.5) * 0.02)))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal high = price.max(open).multiply(BigDecimal.ONE.add(BigDecimal.valueOf(random.nextDouble() * 0.01)))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal low = price.min(open).multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(random.nextDouble() * 0.01)))
                .setScale(2, RoundingMode.HALF_UP);
        
        Long volume = 1000000L + random.nextInt(9000000);
        
        StockData stockData = new StockData();
        stockData.setSymbol(symbol.toUpperCase());
        stockData.setPrice(price);
        stockData.setChange(change);
        stockData.setChangePercent(changePercentBD);
        stockData.setOpen(open);
        stockData.setHigh(high);
        stockData.setLow(low);
        stockData.setPreviousClose(basePrice);
        stockData.setVolume(volume);
        stockData.setTimestamp(LocalDateTime.now());
        
        // Update base price for next call
        basePrices.put(symbol.toUpperCase(), price);
        
        return stockData;
    }

    public List<StockData> generateMockStockDataForSymbols(List<String> symbols) {
        List<StockData> stockDataList = new ArrayList<>();
        for (String symbol : symbols) {
            stockDataList.add(generateMockStockData(symbol));
        }
        return stockDataList;
    }

    public List<StockData> generateAllMockStockData() {
        return generateMockStockDataForSymbols(new ArrayList<>(basePrices.keySet()));
    }

    public Map<String, Object> generateMarketSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("timestamp", LocalDateTime.now());
        summary.put("totalStocks", basePrices.size());
        
        List<StockData> allStocks = generateAllMockStockData();
        
        // Calculate market statistics
        BigDecimal totalMarketCap = allStocks.stream()
                .map(StockData::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long totalVolume = allStocks.stream()
                .mapToLong(StockData::getVolume)
                .sum();
        
        long advancingStocks = allStocks.stream()
                .filter(stock -> stock.getChange().compareTo(BigDecimal.ZERO) > 0)
                .count();
        
        long decliningStocks = allStocks.stream()
                .filter(stock -> stock.getChange().compareTo(BigDecimal.ZERO) < 0)
                .count();
        
        summary.put("totalMarketCap", totalMarketCap);
        summary.put("totalVolume", totalVolume);
        summary.put("advancingStocks", advancingStocks);
        summary.put("decliningStocks", decliningStocks);
        summary.put("unchangedStocks", basePrices.size() - advancingStocks - decliningStocks);
        
        return summary;
    }

    public List<StockData> generateHistoricalData(String symbol, int days) {
        List<StockData> historicalData = new ArrayList<>();
        BigDecimal basePrice = basePrices.getOrDefault(symbol.toUpperCase(), new BigDecimal("100.00"));
        
        LocalDateTime currentTime = LocalDateTime.now();
        
        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime date = currentTime.minusDays(i);
            
            // Generate price with some trend and volatility
            double trend = Math.sin(i * 0.1) * 0.02; // Small trend
            double volatility = (random.nextDouble() - 0.5) * 0.03; // ±1.5% daily volatility
            double totalChange = trend + volatility;
            
            BigDecimal price = basePrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(totalChange)))
                    .setScale(2, RoundingMode.HALF_UP);
            
            BigDecimal open = basePrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf((random.nextDouble() - 0.5) * 0.01)))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal high = price.max(open).multiply(BigDecimal.ONE.add(BigDecimal.valueOf(random.nextDouble() * 0.005)))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal low = price.min(open).multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(random.nextDouble() * 0.005)))
                    .setScale(2, RoundingMode.HALF_UP);
            
            BigDecimal change = price.subtract(basePrice);
            BigDecimal changePercent = change.divide(basePrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            
            Long volume = 500000L + random.nextInt(5000000);
            
            StockData stockData = new StockData();
            stockData.setSymbol(symbol.toUpperCase());
            stockData.setPrice(price);
            stockData.setChange(change);
            stockData.setChangePercent(changePercent);
            stockData.setOpen(open);
            stockData.setHigh(high);
            stockData.setLow(low);
            stockData.setPreviousClose(basePrice);
            stockData.setVolume(volume);
            stockData.setTimestamp(date);
            
            historicalData.add(stockData);
            basePrice = price; // Use current price as base for next day
        }
        
        return historicalData;
    }
} 