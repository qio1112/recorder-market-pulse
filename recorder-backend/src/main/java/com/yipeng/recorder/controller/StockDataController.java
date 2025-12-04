package com.yipeng.recorder.controller;

import com.yipeng.recorder.exception.ForbiddenException;
import com.yipeng.recorder.model.StockDailyHistory;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.request.StockHistoryRequest;
import com.yipeng.recorder.response.StockDailyHistoryForSymbolResponse;
import com.yipeng.recorder.response.StockDailyHistoryFullResponse;
import com.yipeng.recorder.service.StockDailyHistoryService;
import com.yipeng.recorder.service.StockDataScriptService;
import com.yipeng.recorder.service.UserService;

import com.yipeng.recorder.utils.DateTimeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class StockDataController {

    private static final Logger logger = LoggerFactory.getLogger(StockDataController.class);

    private final UserService userService;
    private final StockDataScriptService stockDataScriptService;
    private final StockDailyHistoryService stockDailyHistoryService;
    private final DateTimeUtils dateTimeUtils;

    @Autowired
    public StockDataController(UserService userService,
                               StockDataScriptService stockDataScriptService,
                               StockDailyHistoryService stockDailyHistoryService,
                               DateTimeUtils dateTimeUtils
                               ) {
        this.userService = userService;
        this.stockDataScriptService = stockDataScriptService;
        this.stockDailyHistoryService = stockDailyHistoryService;
        this.dateTimeUtils = dateTimeUtils;
    }

    @PostMapping("/stock-data/get-daily-history")
    public ResponseEntity<StockDailyHistoryFullResponse> getStockDailyHistory(@RequestBody StockHistoryRequest body) {
        User user = userService.findUserFromAuthentication();
        if (user == null || !user.isAdmin()) {
            throw new ForbiddenException();
        }
        List<String> symbols = this.stockDataScriptService.formatSymbolList(body.getSymbols());
        Map<String, List<StockDailyHistory>> rawData = this.stockDailyHistoryService.getHistoryBySymbols(symbols);
        List<String> invalidSymbols = new ArrayList<>(symbols);
        invalidSymbols.removeAll(rawData.keySet());
        List<StockDailyHistoryForSymbolResponse> responseForSymbols = rawData.keySet().stream()
                .map(symbol -> new StockDailyHistoryForSymbolResponse(rawData.get(symbol), symbol))
                .toList();
        StockDailyHistoryFullResponse response = new StockDailyHistoryFullResponse(invalidSymbols, responseForSymbols);
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/stock-data/update-daily-history-db")
    public ResponseEntity<String> updateStockDailyHistory(@RequestBody StockHistoryRequest body) {
        User user = userService.findUserFromAuthentication();
        if (user == null || !user.isAdmin()) {
            throw new ForbiddenException();
        }

        Map<String, List<StockDailyHistory>> stockHistoryBySymbolFromApi = this.stockDataScriptService.getStockDailyHistoryFromScript(body.getSymbols(), user);
        this.stockDailyHistoryService.updateStockDailyHistoryDatabase(stockHistoryBySymbolFromApi);

        return ResponseEntity.ok().body("Stock daily history data successfully updated for symbols: " + StringUtils.join(stockHistoryBySymbolFromApi.keySet(), ","));
    }

    @PostMapping("/run-script/update_stock_data")
    public ResponseEntity<String> runUpdateStockScript(@RequestBody Map<String, String> arguments) {
        User user = userService.findUserFromAuthentication();
        if (user == null || !user.isAdmin()) {
            throw new ForbiddenException();
        }
        String output = stockDataScriptService.runUpdateStockDataScript(arguments, user);

        if (!output.startsWith("exitCode=0")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Script execution failed" + ".\nOutput:\n" + output);
        }

        logger.info("Script update_stock_data execution completed successfully.");
        String prefix = "result data:";
        if (output.contains(prefix)) {
            output = output.substring(output.indexOf(prefix) + prefix.length()).trim();
        }
        return ResponseEntity.ok().body(output);
    }

    @GetMapping(value="/run-script/update_stock_data/help")
    public ResponseEntity<String> updateStockScriptHelp() {
        User user = userService.findUserFromAuthentication();
        if (user == null || !user.isAdmin()) {
            throw new ForbiddenException();
        }
        String help = """
               JSON Arguments for Stock and Option Data Task
               You can send the following arguments:

              \s
               "jobName": string or null,
               "symbolPath": string or null,
               "optionSymbolPath": string or null,
               "symbols": array of strings or null,
               "optionSymbols": array of strings or null,
               "taskLabel": string,
               "maxWorkers": integer,
               "update_most_recent_date": boolean
              \s

               Field Details:

               1. jobName (string, default: "update_stock_data")
                  Determines which task will run.
                  Allowed values:
                    - "update_stock_data"
                    - "update_stock_data_flexible"
                    - "get_current_minute_stock_price_json"
                    - "get_stock_price_day_history_json"
                    - "get_fear_greed_index_data"

               2. symbolPath (string or null, default: null)
                  Path to a file containing stock symbols (one symbol per line).
                  Used only if 'symbols' is not provided.

               3. optionSymbolPath (string or null, default: null)
                  Path to a file containing option symbols (one per line).
                  Used only if 'optionSymbols' is not provided.

               4. symbols (array of strings or null, default: null)
                  Direct list of stock symbols.
                  Example: ["AAPL", "MSFT", "TSLA"]

               5. optionSymbols (array of strings or null, default: null)
                  Direct list of option symbols.
                  Example: ["AAPL240621C00100000"]

               6. taskLabel (string, default: "close")
                  Used only by the flexible update job ("update_stock_data_flexible").
                  Identifies the type of data update task.

               7. maxWorkers (integer, default: 8)
                  Number of parallel workers for the flexible update job.

               8. update_most_recent_date (boolean, default: false)
                  Controls which date should be updated.
                    - false → update today's data (if market open)
                    - true  → update the previous trading day's data

               Behavior Summary:

               - For jobName = "update_stock_data":
                     Updates stock + option data for the selected date.
                     Uses symbols from 'symbols' or 'symbolPath'.

               - For jobName = "update_stock_data_flexible":
                     Flexible update pipeline.
                     Uses 'taskLabel', 'maxWorkers', and symbol lists.
                     Updates today unless update_most_recent_date = true.

               - For jobName = "get_current_minute_stock_price_json":
                     Returns recent minute-level stock prices as JSON.
                     Requires 'symbols'.

               - For jobName = "get_stock_price_day_history_json":
                     Returns historical daily prices in JSON.
                     Requires 'symbols'.
              
               - For jobName = "get_today_is_trade_day":
                     Returns boolean value about whether current day is a trade date

               - For jobName = "get_fear_greed_index_data":
                     Returns CNN Fear & Greed Index data.
                     No additional fields required.
               \s""";
        return ResponseEntity.ok(help);
    }
}

