package com.yipeng.recorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yipeng.recorder.model.StockDailyHistory;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.utils.RunScriptResult;
import com.yipeng.recorder.utils.ScriptUtil;
import com.yipeng.recorder.response.StockDailyHistoryForSymbolResponse;
import jakarta.mail.MessagingException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class StockDataScriptService {

    private static final Logger logger = LoggerFactory.getLogger(StockDataScriptService.class);

    private final SendEmailService sendEmailService;

    @Value("${MARKET_PULSE_PATH}")
    private String marketPulsePath;

    @Autowired
    public StockDataScriptService(SendEmailService sendEmailService) {
        this.sendEmailService = sendEmailService;
    }

    public String runUpdateStockDataScript(Map<String, String> arguments, User user) {
        RunScriptResult runScriptResult = ScriptUtil.runScript("update_stock_data", arguments);
        int exitCode = runScriptResult.getExitCode();
        String output = runScriptResult.getOutput();

        // send email
        if (user != null && arguments.containsKey("sendEmail") && arguments.get("sendEmail").equalsIgnoreCase("true")) {
            String userEmail = user.getEmail();
            try {
                if (StringUtils.isNotBlank(userEmail)) {
                    sendEmailService.sendEmail(userEmail, "Run script: update_stock_data", output, null);
                }
                logger.info("Email sent to user {} for running script update_stock_data", user.getUsername());
            } catch (MessagingException e) {
                logger.warn("Failed to send email to user {} for running script update_stock_data: {}", user.getUsername(), e.getMessage());
            }
        }
        return "exitCode=" + exitCode + "\n" + output;
    }

    public List<String> formatSymbolList(List<String> symbolsList) {
        return symbolsList.stream()
                .filter(StringUtils::isNotBlank)
                .map(sym -> sym.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public List<String> getDefaultTrackingStockSymbolsFromMarketPulse() {
        try {
            String defaultSymbolsFilePath = this.marketPulsePath + "/resources/symbols/symbols.txt";
            Path path = Path.of(defaultSymbolsFilePath);
            List<String> defaultSymbols = new ArrayList<>();
            if (!Files.exists(path)) {
                System.out.println("File does not exist: " + defaultSymbolsFilePath);
                defaultSymbols = Collections.emptyList();
            } else {
                defaultSymbols = Files.readAllLines(path);
            }
            return defaultSymbols;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public Map<String, List<StockDailyHistory>> getStockDailyHistoryFromScript(List<String> symbolsList, User user) {

        if (symbolsList == null || symbolsList.isEmpty()) {
            symbolsList = this.getDefaultTrackingStockSymbolsFromMarketPulse();
            logger.info("No input symbols, using default symbols: {}", StringUtils.join(symbolsList, ","));
        }
        if (symbolsList.isEmpty()) {
            return new HashMap<>();
        }

        List<String> symbols = this.formatSymbolList(symbolsList);
        logger.info("Starting job to update stock daily history data in database.");
        Map<String, String> arguments = new HashMap<>();
        arguments.put("jobName", "get_stock_price_day_history_json");
        arguments.put("symbols", StringUtils.join(symbols, ","));
        String prefix = "result data:";
        String apiOutput = this.runUpdateStockDataScript(arguments, user);
        if (!apiOutput.startsWith("exitCode=0")) {
            throw new RuntimeException("Failed getting stock daily history data from script.");
        }
        apiOutput = apiOutput.substring(apiOutput.indexOf(prefix) + prefix.length()).trim();
        logger.info("API call to get stock data succeeded, parsing and validating data.");

        Map<String, List<StockDailyHistory>> stockHistoryBySymbolFromApi = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(apiOutput);
            JsonNode dataNode = root.get("Data");
            List<StockDailyHistoryForSymbolResponse> data = mapper.convertValue(dataNode, new TypeReference<>() {});

            data.forEach(response -> {
                stockHistoryBySymbolFromApi.put(response.getSymbol(), response.convertToStockDailyHistory());
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Not able to parse response to json: " + apiOutput);
        }
        logger.info("Response data parsed successfully, updating for symbols: {}", StringUtils.join(stockHistoryBySymbolFromApi.keySet(), ","));
        logger.info("Checking what to be updated in database.");
        return stockHistoryBySymbolFromApi;
    }
}
