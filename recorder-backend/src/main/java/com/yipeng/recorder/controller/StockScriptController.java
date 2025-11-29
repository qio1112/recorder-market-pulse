package com.yipeng.recorder.controller;

import com.yipeng.recorder.exception.ForbiddenException;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.service.StockDataScriptService;
import com.yipeng.recorder.service.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class StockScriptController {

    private static final Logger logger = LoggerFactory.getLogger(StockScriptController.class);

    private final UserService userService;
    private final StockDataScriptService stockDataScriptService;

    @Autowired
    public StockScriptController(UserService userService,
                                 StockDataScriptService stockDataScriptService) {
        this.userService = userService;
        this.stockDataScriptService = stockDataScriptService;
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
        return ResponseEntity.ok("Script update_stock_data executed successfully.\nOutput:\n" + output);
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

               
               "jobName": string or null,
               "symbolPath": string or null,
               "optionSymbolPath": string or null,
               "symbols": array of strings or null,
               "optionSymbols": array of strings or null,
               "taskLabel": string,
               "maxWorkers": integer,
               "update_most_recent_date": boolean
               

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

               - For jobName = "get_fear_greed_index_data":
                     Returns CNN Fear & Greed Index data.
                     No additional fields required.
               \s""";
        return ResponseEntity.ok(help);
    }
}

