package com.yipeng.stockapi.controller;

import com.yipeng.stockapi.model.StockData;
import com.yipeng.stockapi.service.MockStockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MockStockDataService mockStockDataService;

    @MessageMapping("/subscribe")
    @SendTo("/topic/stock-updates")
    public StockData subscribeToStock(String symbol) {
        log.info("Client subscribed to stock: {}", symbol);
        return mockStockDataService.generateMockStockData(symbol);
    }

    @MessageMapping("/subscribe-multiple")
    @SendTo("/topic/multiple-stock-updates")
    public List<StockData> subscribeToMultipleStocks(List<String> symbols) {
        log.info("Client subscribed to multiple stocks: {}", symbols);
        return mockStockDataService.generateMockStockDataForSymbols(symbols);
    }

    @MessageMapping("/market-summary")
    @SendTo("/topic/market-summary")
    public Map<String, Object> getMarketSummary() {
        log.info("Client requested market summary");
        return mockStockDataService.generateMarketSummary();
    }

    // Method to broadcast stock updates to all connected clients
    public void broadcastStockUpdate(StockData stockData) {
        messagingTemplate.convertAndSend("/topic/stock-updates", stockData);
    }

    // Method to broadcast multiple stock updates
    public void broadcastMultipleStockUpdates(List<StockData> stockDataList) {
        messagingTemplate.convertAndSend("/topic/multiple-stock-updates", stockDataList);
    }

    // Method to broadcast market summary
    public void broadcastMarketSummary(Map<String, Object> marketSummary) {
        messagingTemplate.convertAndSend("/topic/market-summary", marketSummary);
    }

    // Method to send stock update to specific user
    public void sendStockUpdateToUser(String username, StockData stockData) {
        messagingTemplate.convertAndSendToUser(username, "/queue/stock-updates", stockData);
    }
} 