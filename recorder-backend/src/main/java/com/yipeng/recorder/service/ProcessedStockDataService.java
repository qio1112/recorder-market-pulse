package com.yipeng.recorder.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ProcessedStockDataService {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessedStockDataService.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${data-processor.base-url:http://data-processor:8082}")
    private String dataProcessorBaseUrl;
    
    @Autowired
    public ProcessedStockDataService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Get recent processed stock data for a specific symbol
     */
    public List<Map<String, Object>> getProcessedDataBySymbol(String symbol, int limit) {
        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(dataProcessorBaseUrl)
                .path("/api/data-processor/processed-data/{symbol}")
                .queryParam("limit", limit)
                .buildAndExpand(symbol)
                .toUriString();
            
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Retrieved {} processed data records for symbol: {}", response.getBody().size(), symbol);
                return response.getBody();
            } else {
                logger.warn("Failed to retrieve processed data for symbol: {}, status: {}", symbol, response.getStatusCode());
                return List.of();
            }
        } catch (Exception e) {
            logger.error("Error retrieving processed data for symbol: {}", symbol, e);
            return List.of();
        }
    }
    
    /**
     * Get recent processed stock data with optional symbol filter
     */
    public List<Map<String, Object>> getRecentProcessedData(String symbol, LocalDateTime since, int limit) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(dataProcessorBaseUrl)
                .path("/api/data-processor/processed-data")
                .queryParam("limit", limit);
            
            if (symbol != null && !symbol.trim().isEmpty()) {
                builder.queryParam("symbol", symbol);
            }
            
            if (since != null) {
                builder.queryParam("since", since.toString());
            }
            
            String url = builder.toUriString();
            
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Retrieved {} recent processed data records", response.getBody().size());
                return response.getBody();
            } else {
                logger.warn("Failed to retrieve recent processed data, status: {}", response.getStatusCode());
                return List.of();
            }
        } catch (Exception e) {
            logger.error("Error retrieving recent processed data", e);
            return List.of();
        }
    }
    
    /**
     * Get processed data within a time range
     */
    public List<Map<String, Object>> getProcessedDataInRange(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(dataProcessorBaseUrl)
                .path("/api/data-processor/processed-data/range")
                .queryParam("startTime", startTime.toString())
                .queryParam("endTime", endTime.toString());
            
            if (symbol != null && !symbol.trim().isEmpty()) {
                builder.queryParam("symbol", symbol);
            }
            
            String url = builder.toUriString();
            
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Retrieved {} processed data records in range", response.getBody().size());
                return response.getBody();
            } else {
                logger.warn("Failed to retrieve processed data in range, status: {}", response.getStatusCode());
                return List.of();
            }
        } catch (Exception e) {
            logger.error("Error retrieving processed data in range", e);
            return List.of();
        }
    }
    
    /**
     * Get processed metrics for a specific symbol
     */
    public List<Map<String, Object>> getProcessedMetricsBySymbol(String symbol, int limit) {
        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(dataProcessorBaseUrl)
                .path("/api/data-processor/processed-data/{symbol}/metrics")
                .queryParam("limit", limit)
                .buildAndExpand(symbol)
                .toUriString();
            
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Retrieved {} processed metrics records for symbol: {}", response.getBody().size(), symbol);
                return response.getBody();
            } else {
                logger.warn("Failed to retrieve processed metrics for symbol: {}, status: {}", symbol, response.getStatusCode());
                return List.of();
            }
        } catch (Exception e) {
            logger.error("Error retrieving processed metrics for symbol: {}", symbol, e);
            return List.of();
        }
    }
    
    /**
     * Get the status of the data processing pipeline
     */
    public Optional<Map<String, Object>> getDataProcessorStatus() {
        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(dataProcessorBaseUrl)
                .path("/api/data-processor/status")
                .toUriString();
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Retrieved data processor status: {}", response.getBody());
                return Optional.of(response.getBody());
            } else {
                logger.warn("Failed to retrieve data processor status, status: {}", response.getStatusCode());
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.error("Error retrieving data processor status", e);
            return Optional.empty();
        }
    }
    
    /**
     * Start the data processing pipeline
     */
    public boolean startDataProcessing() {
        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(dataProcessorBaseUrl)
                .path("/api/data-processor/start")
                .toUriString();
            
            ResponseEntity<Map<String, String>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<Map<String, String>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("Data processing pipeline started: {}", response.getBody());
                return "success".equals(response.getBody().get("status"));
            } else {
                logger.warn("Failed to start data processing pipeline, status: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error starting data processing pipeline", e);
            return false;
        }
    }
    
    /**
     * Stop the data processing pipeline
     */
    public boolean stopDataProcessing() {
        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(dataProcessorBaseUrl)
                .path("/api/data-processor/stop")
                .toUriString();
            
            ResponseEntity<Map<String, String>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<Map<String, String>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("Data processing pipeline stopped: {}", response.getBody());
                return "success".equals(response.getBody().get("status"));
            } else {
                logger.warn("Failed to stop data processing pipeline, status: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error stopping data processing pipeline", e);
            return false;
        }
    }
} 