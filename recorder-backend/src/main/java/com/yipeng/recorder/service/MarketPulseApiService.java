package com.yipeng.recorder.service;

import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.recorder.model.StockDailyHistory;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.response.StockDailyHistoryForSymbolResponse;
import jakarta.mail.MessagingException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.yipeng.recorder.request.MarketPulseUpdateStockDataRequest;

@Service
public class MarketPulseApiService {

    private static final Logger logger = LoggerFactory.getLogger(MarketPulseApiService.class);

    private final SendEmailService sendEmailService;

    private final RestTemplate restTemplate;

    @Value("${market.pulse.url}")
    private String marketPulseBaseUrl;

    @Autowired
    public MarketPulseApiService(RestTemplate restTemplate, SendEmailService sendEmailService) {
        this.restTemplate = restTemplate;
        this.sendEmailService = sendEmailService;
    }

    public ResponseEntity<String> getMarketPulseServerStatus() {
        String url = marketPulseBaseUrl + "/health";
        return restTemplate.getForEntity(url, String.class);
    }

    public String runUpdateStockOptionDataApi(MarketPulseUpdateStockDataRequest request) {
        String url = marketPulseBaseUrl + "/update-stock-data";
        MarketPulseUpdateStockDataRequest payload = request == null
                ? new MarketPulseUpdateStockDataRequest()
                : request;
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(url, buildJsonRequest(payload), String.class);
        if (responseEntity.getStatusCode().isError()) {
            throw new RuntimeException("Failed to runUpdateStockDataApi. Status code: " + responseEntity.getStatusCode().value());
        }
        return responseEntity.getBody();
    }

    public Map<String, List<StockDailyHistory>> getStockDailyHistory(List<String> symbolsList) {
        String url = marketPulseBaseUrl + "/stock-daily-history";
        if (symbolsList == null || symbolsList.isEmpty()) {
            symbolsList = this.getTrackedSymbols(false);
            logger.info("No input symbols, using default symbols: {}", StringUtils.join(symbolsList, ","));
        }
        if (symbolsList.isEmpty()) {
            return new HashMap<>();
        }
        List<String> symbols = this.formatSymbolList(symbolsList);
        logger.info("Starting job to update stock daily history data in database.");

        ResponseEntity<String> apiResponseEntity = restTemplate.postForEntity(url, buildSymbolsRequest(symbols), String.class);
        if (apiResponseEntity.getStatusCode().isError()) {
            throw new RuntimeException("Failed getting stock daily history data from script.");
        }
        String apiResponse = apiResponseEntity.getBody();
        logger.info("API call to get stock data succeeded, parsing and validating data.");

        Map<String, List<StockDailyHistory>> stockHistoryBySymbolFromApi = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(apiResponse);
            JsonNode dataNode = root.get("Data");
            List<StockDailyHistoryForSymbolResponse> data = mapper.convertValue(dataNode, new TypeReference<>() {});

            data.forEach(response -> {
                stockHistoryBySymbolFromApi.put(response.getSymbol(), response.convertToStockDailyHistory());
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Not able to parse response to json: " + apiResponse);
        }
        logger.info("Response data parsed successfully, updating for symbols: {}", StringUtils.join(stockHistoryBySymbolFromApi.keySet(), ","));
        return stockHistoryBySymbolFromApi;
    }


    public List<String> getTrackedSymbols(boolean forOption) {
        String url = marketPulseBaseUrl + "/stock-symbols";
        if (forOption) {
            url = marketPulseBaseUrl + "/option-symbols";
        }
        ResponseEntity<Map<String, List<String>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, List<String>>>() {
                });
        Map<String, List<String>> body = response.getBody();
        return body != null && body.get("symbols") != null
                ? body.get("symbols")
                : Collections.emptyList();
    }

    public ResponseEntity<String> getFearGreedIndex() {
        String url = marketPulseBaseUrl + "/fear-greed-index";
        return restTemplate.getForEntity(url, String.class);
    }

    private HttpEntity<Map<String, List<String>>> buildSymbolsRequest(List<String> symbols) {
        Map<String, List<String>> body = new HashMap<>();
        body.put("symbols", symbols);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(body, headers);
    }

    private <T> HttpEntity<T> buildJsonRequest(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    public List<String> formatSymbolList(List<String> symbolsList) {
        return symbolsList.stream()
                .filter(StringUtils::isNotBlank)
                .map(sym -> sym.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
