package com.yipeng.recorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.recorder.model.StockDailyHistory;
import com.yipeng.recorder.prompt.BuiltInLlmTokenLimits;
import com.yipeng.recorder.prompt.BuiltInPrompts;
import com.yipeng.recorder.request.LlmChatRequest;
import com.yipeng.recorder.response.OptionExpiryDatesResponse;
import com.yipeng.recorder.response.OptionHistoryResponse;
import com.yipeng.recorder.response.OptionParquetCombineResponse;
import com.yipeng.recorder.response.OptionSymbolsResponse;
import com.yipeng.recorder.response.LlmChatResponse;
import com.yipeng.recorder.response.StockDailyHistoryForSymbolResponse;
import com.yipeng.recorder.response.StockNewsSummaryResponse;
import jakarta.mail.MessagingException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.yipeng.recorder.request.MarketPulseUpdateStockDataRequest;

import java.time.Duration;
import java.util.*;

@Service
public class MarketPulseApiService {

    private static final Logger logger = LoggerFactory.getLogger(MarketPulseApiService.class);

    private final SendEmailService sendEmailService;

    private final RestTemplate restTemplate;
    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${market.pulse.url}")
    private String marketPulseBaseUrl;

    @Autowired
    public MarketPulseApiService(RestTemplate restTemplate,
                                 RestTemplateBuilder restTemplateBuilder,
                                 SendEmailService sendEmailService) {
        this.restTemplate = restTemplate;
        this.restTemplateBuilder = restTemplateBuilder;
        this.sendEmailService = sendEmailService;
    }

    public ResponseEntity<String> getMarketPulseServerStatus() {
        String url = marketPulseBaseUrl + "/health";
        return restTemplate.getForEntity(url, String.class);
    }

    public boolean isTodayTradeDay() {
        String url = marketPulseBaseUrl + "/today-is-trade-day";
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
        Map<String, Object> body = response.getBody();
        Object value = body == null ? null : body.get("is_trade_day");
        return value instanceof Boolean && (Boolean) value;
    }

    public String runUpdateStockOptionDataApi(MarketPulseUpdateStockDataRequest request) {
        String url = marketPulseBaseUrl + "/update-stock-data";
        MarketPulseUpdateStockDataRequest payload = request == null
                ? new MarketPulseUpdateStockDataRequest()
                : request;
        logger.info("Started updating stock option data.");
        // Use a longer timeout for this potentially slow operation
        RestTemplate longTimeoutRestTemplate = buildRestTemplateWithTimeouts(Duration.ofSeconds(10), Duration.ofMinutes(5));
        ResponseEntity<String> responseEntity = longTimeoutRestTemplate.postForEntity(url, buildJsonRequest(payload), String.class);
        if (responseEntity.getStatusCode().isError()) {
            throw new RuntimeException("Failed to runUpdateStockDataApi. Status code: " + responseEntity.getStatusCode().value());
        }
        logger.info("Finished updating stock option data.");
        String body = responseEntity.getBody();
        if (body == null) {
            return null;
        }
        try {
            // Response may be a JSON-encoded string (e.g., "\"line1\\nline2\""), so unwrap it
            return objectMapper.readValue(body, String.class);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to unwrap JSON string response, returning raw body");
            return body;
        }
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

        RestTemplate longTimeoutRestTemplate = buildRestTemplateWithTimeouts(Duration.ofSeconds(10), Duration.ofMinutes(5));
        ResponseEntity<String> apiResponseEntity = longTimeoutRestTemplate.postForEntity(url, buildSymbolsRequest(symbols), String.class);
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

    public OptionSymbolsResponse getOptionSymbols() {
        String url = marketPulseBaseUrl + "/options/symbols";
        logger.info("Calling Market Pulse option symbols API: {}", url);
        ResponseEntity<OptionSymbolsResponse> response = restTemplate.postForEntity(
                url,
                buildJsonRequest(Collections.emptyMap()),
                OptionSymbolsResponse.class
        );
        OptionSymbolsResponse body = response.getBody() == null ? new OptionSymbolsResponse() : response.getBody();
        logger.info("Market Pulse option symbols API returned {} symbol(s)", body.getSymbols().size());
        return body;
    }

    public OptionExpiryDatesResponse getOptionExpiryDates(String symbol) {
        String url = marketPulseBaseUrl + "/options/expiry-dates";
        logger.info("Calling Market Pulse option expiry API for symbol={}: {}", symbol, url);
        Map<String, String> payload = new HashMap<>();
        payload.put("symbol", symbol);
        ResponseEntity<OptionExpiryDatesResponse> response = restTemplate.postForEntity(
                url,
                buildJsonRequest(payload),
                OptionExpiryDatesResponse.class
        );
        OptionExpiryDatesResponse body = response.getBody() == null ? new OptionExpiryDatesResponse() : response.getBody();
        logger.info(
                "Market Pulse option expiry API returned {} expiry date(s) for symbol={}",
                body.getExpiryDates().size(),
                symbol
        );
        return body;
    }

    public OptionHistoryResponse getOptionHistory(String symbol, String expiry, String optionType) {
        String url = marketPulseBaseUrl + "/options/history";
        logger.info(
                "Calling Market Pulse option history API for symbol={}, expiry={}, optionType={}: {}",
                symbol,
                expiry,
                optionType,
                url
        );
        Map<String, String> payload = new HashMap<>();
        payload.put("symbol", symbol);
        payload.put("expiry", expiry);
        payload.put("option_type", optionType);
        ResponseEntity<OptionHistoryResponse> response = restTemplate.postForEntity(
                url,
                buildJsonRequest(payload),
                OptionHistoryResponse.class
        );
        OptionHistoryResponse body = response.getBody() == null ? new OptionHistoryResponse() : response.getBody();
        logger.info(
                "Market Pulse option history API returned {} strike(s) for symbol={}, expiry={}, optionType={}",
                body.getStrikes().size(),
                symbol,
                expiry,
                optionType
        );
        return body;
    }

    public OptionParquetCombineResponse combineExpiredOptionParquetFiles() {
        String url = marketPulseBaseUrl + "/options/combine-expired-parquet";
        logger.info("Calling Market Pulse combine expired option parquet API: {}", url);
        ResponseEntity<OptionParquetCombineResponse> response = restTemplate.getForEntity(
                url,
                OptionParquetCombineResponse.class
        );
        OptionParquetCombineResponse body = response.getBody() == null ? new OptionParquetCombineResponse() : response.getBody();
        logger.info("Market Pulse combine expired option parquet API combined {} expiry/type folder(s)",
                body.getCombinedCount());
        return body;
    }

    public ResponseEntity<String> getFearGreedIndex() {
        String url = marketPulseBaseUrl + "/fear-greed-index";
        return restTemplate.getForEntity(url, String.class);
    }

    public StockNewsSummaryResponse getTrackedStockNewsSummary() {
        String url = marketPulseBaseUrl + "/news/stock-summary";
        logger.info("Calling Market Pulse stock news summary API: {}", url);
        RestTemplate longTimeoutRestTemplate = buildRestTemplateWithTimeouts(Duration.ofSeconds(10), Duration.ofMinutes(20));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary_prompt", BuiltInPrompts.MARKET_NEWS_SUMMARY_SYSTEM_PROMPT);
        payload.put("max_tokens", BuiltInLlmTokenLimits.MARKET_NEWS_SUMMARY_MAX_TOKENS);
        ResponseEntity<StockNewsSummaryResponse> response = longTimeoutRestTemplate.postForEntity(
                url,
                buildJsonRequest(payload),
                StockNewsSummaryResponse.class
        );
        StockNewsSummaryResponse body = response.getBody() == null ? new StockNewsSummaryResponse() : response.getBody();
        logger.info("Market Pulse stock news summary API returned {} symbol summary item(s)", body.getSummaries().size());
        return body;
    }

    public LlmChatResponse chatWithLlm(LlmChatRequest request) {
        return chatWithLlm(request, Duration.ofSeconds(120));
    }

    public LlmChatResponse chatWithLlm(LlmChatRequest request, Duration readTimeout) {
        String url = marketPulseBaseUrl + "/llm/chat";
        logger.info("Calling Market Pulse LLM chat API: {}", url);
        RestTemplate llmTimeoutRestTemplate = buildRestTemplateWithTimeouts(Duration.ofSeconds(10), readTimeout);
        ResponseEntity<LlmChatResponse> response = llmTimeoutRestTemplate.postForEntity(
                url,
                buildJsonRequest(request),
                LlmChatResponse.class
        );
        return response.getBody() == null ? new LlmChatResponse("") : response.getBody();
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

    private RestTemplate buildRestTemplateWithTimeouts(Duration connectTimeout, Duration readTimeout) {
        return restTemplateBuilder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }
}
