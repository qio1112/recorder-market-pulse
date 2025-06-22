package com.yipeng.stockapi.service;

import com.yipeng.stockapi.controller.WebSocketController;
import com.yipeng.stockapi.model.StockData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockDataBroadcastService {

    private final MockStockDataService mockStockDataService;
    private final WebSocketController webSocketController;

    // Broadcast individual stock updates every 5 seconds
    @Scheduled(fixedRate = 5000)
    public void broadcastStockUpdates() {
        try {
            List<String> popularStocks = List.of("AAPL", "GOOGL", "MSFT", "AMZN", "TSLA");
            
            for (String symbol : popularStocks) {
                StockData stockData = mockStockDataService.generateMockStockData(symbol);
                webSocketController.broadcastStockUpdate(stockData);
                log.debug("Broadcasted stock update for: {}", symbol);
            }
        } catch (Exception e) {
            log.error("Error broadcasting stock updates", e);
        }
    }

    // Broadcast market summary every 30 seconds
    @Scheduled(fixedRate = 30000)
    public void broadcastMarketSummary() {
        try {
            Map<String, Object> marketSummary = mockStockDataService.generateMarketSummary();
            webSocketController.broadcastMarketSummary(marketSummary);
            log.debug("Broadcasted market summary");
        } catch (Exception e) {
            log.error("Error broadcasting market summary", e);
        }
    }

    // Broadcast all stock data every 10 seconds
    @Scheduled(fixedRate = 10000)
    public void broadcastAllStockData() {
        try {
            List<StockData> allStockData = mockStockDataService.generateAllMockStockData();
            webSocketController.broadcastMultipleStockUpdates(allStockData);
            log.debug("Broadcasted all stock data for {} stocks", allStockData.size());
        } catch (Exception e) {
            log.error("Error broadcasting all stock data", e);
        }
    }

    // Generate and broadcast high-frequency updates for specific stocks every 2 seconds
    @Scheduled(fixedRate = 2000)
    public void broadcastHighFrequencyUpdates() {
        try {
            List<String> highFrequencyStocks = List.of("SPY", "QQQ", "NVDA", "META");
            List<StockData> highFreqData = mockStockDataService.generateMockStockDataForSymbols(highFrequencyStocks);
            webSocketController.broadcastMultipleStockUpdates(highFreqData);
            log.debug("Broadcasted high-frequency updates for {} stocks", highFreqData.size());
        } catch (Exception e) {
            log.error("Error broadcasting high-frequency updates", e);
        }
    }
} 