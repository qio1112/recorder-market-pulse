package com.yipeng.recorder.controller;

import com.yipeng.recorder.service.ProcessedStockDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/processed-stock-data")
public class ProcessedStockDataController {
    
    private final ProcessedStockDataService processedStockDataService;
    
    @Autowired
    public ProcessedStockDataController(ProcessedStockDataService processedStockDataService) {
        this.processedStockDataService = processedStockDataService;
    }
    
    /**
     * Get recent processed stock data for a specific symbol
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<Map<String, Object>>> getProcessedDataBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        
        List<Map<String, Object>> data = processedStockDataService.getProcessedDataBySymbol(symbol, limit);
        return ResponseEntity.ok(data);
    }
    
    /**
     * Get recent processed stock data with optional filters
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentProcessedData(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "100") int limit) {
        
        List<Map<String, Object>> data = processedStockDataService.getRecentProcessedData(symbol, since, limit);
        return ResponseEntity.ok(data);
    }
    
    /**
     * Get processed data within a time range
     */
    @GetMapping("/range")
    public ResponseEntity<List<Map<String, Object>>> getProcessedDataInRange(
            @RequestParam(required = false) String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        List<Map<String, Object>> data = processedStockDataService.getProcessedDataInRange(symbol, startTime, endTime);
        return ResponseEntity.ok(data);
    }
    
    /**
     * Get processed metrics for a specific symbol
     */
    @GetMapping("/symbol/{symbol}/metrics")
    public ResponseEntity<List<Map<String, Object>>> getProcessedMetricsBySymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        
        List<Map<String, Object>> metrics = processedStockDataService.getProcessedMetricsBySymbol(symbol, limit);
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Get the status of the data processing pipeline
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getDataProcessorStatus() {
        Optional<Map<String, Object>> status = processedStockDataService.getDataProcessorStatus();
        return status.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Start the data processing pipeline
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startDataProcessing() {
        boolean success = processedStockDataService.startDataProcessing();
        Map<String, Object> response = Map.of(
            "status", success ? "success" : "error",
            "message", success ? "Data processing pipeline started successfully" : "Failed to start data processing pipeline"
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * Stop the data processing pipeline
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopDataProcessing() {
        boolean success = processedStockDataService.stopDataProcessing();
        Map<String, Object> response = Map.of(
            "status", success ? "success" : "error",
            "message", success ? "Data processing pipeline stopped successfully" : "Failed to stop data processing pipeline"
        );
        return ResponseEntity.ok(response);
    }
} 