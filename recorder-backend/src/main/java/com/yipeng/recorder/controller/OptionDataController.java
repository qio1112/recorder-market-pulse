package com.yipeng.recorder.controller;

import com.yipeng.recorder.exception.ForbiddenException;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.request.OptionExpiryDatesRequest;
import com.yipeng.recorder.request.OptionHistoryRequest;
import com.yipeng.recorder.response.OptionExpiryDatesResponse;
import com.yipeng.recorder.response.OptionHistoryResponse;
import com.yipeng.recorder.response.OptionParquetCombineResponse;
import com.yipeng.recorder.response.OptionSymbolsResponse;
import com.yipeng.recorder.service.MarketPulseApiService;
import com.yipeng.recorder.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/option-data")
public class OptionDataController {

    private static final Logger logger = LoggerFactory.getLogger(OptionDataController.class);

    private final UserService userService;
    private final MarketPulseApiService marketPulseApiService;

    @Autowired
    public OptionDataController(UserService userService,
                                MarketPulseApiService marketPulseApiService) {
        this.userService = userService;
        this.marketPulseApiService = marketPulseApiService;
    }

    @GetMapping("/symbols")
    public ResponseEntity<OptionSymbolsResponse> getOptionSymbols() {
        logger.info("Option data request received: get symbols");
        validateAdmin(false);
        return ResponseEntity.ok(marketPulseApiService.getOptionSymbols());
    }

    @PostMapping("/expiries")
    public ResponseEntity<OptionExpiryDatesResponse> getOptionExpiryDates(@RequestBody OptionExpiryDatesRequest request) {
        logger.info("Option data request received: get expiries for symbol={}", request.getSymbol());
        validateAdmin(false);
        String normalizedSymbol = normalizeSymbol(request.getSymbol());
        return ResponseEntity.ok(marketPulseApiService.getOptionExpiryDates(normalizedSymbol));
    }

    @PostMapping("/history")
    public ResponseEntity<OptionHistoryResponse> getOptionHistory(@RequestBody OptionHistoryRequest request) {
        logger.info(
                "Option data request received: get history for symbol={}, expiry={}, optionType={}",
                request.getSymbol(),
                request.getExpiry(),
                request.getOptionType()
        );
        validateAdmin(false);
        String normalizedSymbol = normalizeSymbol(request.getSymbol());
        String normalizedOptionType = request.getOptionType().trim().toLowerCase(Locale.ROOT);
        return ResponseEntity.ok(
                marketPulseApiService.getOptionHistory(normalizedSymbol, request.getExpiry().trim(), normalizedOptionType)
        );
    }

    @GetMapping("/combine-expired-parquet")
    public ResponseEntity<OptionParquetCombineResponse> combineExpiredOptionParquetFiles() {
        logger.info("Option data request received: combine expired parquet files");
        validateAdmin(true);
        return ResponseEntity.ok(marketPulseApiService.combineExpiredOptionParquetFiles());
    }

    private void validateAdmin(boolean adminOnlyAccess) {
        User user = userService.findUserFromAuthentication();
        if (user == null || adminOnlyAccess && !user.isAdmin()) {
            throw new ForbiddenException();
        }
    }

    private String normalizeSymbol(String symbol) {
        if (StringUtils.isBlank(symbol)) {
            throw new IllegalArgumentException("symbol is required");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
