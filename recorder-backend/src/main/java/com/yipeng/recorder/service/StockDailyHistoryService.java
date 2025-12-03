package com.yipeng.recorder.service;

import com.yipeng.recorder.model.StockDailyHistory;
import com.yipeng.recorder.repository.StockDailyHistoryRepository;
import com.yipeng.recorder.utils.DateTimeUtils;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StockDailyHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(StockDailyHistoryService.class);

    private final StockDailyHistoryRepository stockDailyHistoryRepository;

    @Autowired
    public StockDailyHistoryService(StockDailyHistoryRepository stockDailyHistoryRepository) {
        this.stockDailyHistoryRepository = stockDailyHistoryRepository;
    }

    public List<String> getExistingSymbols() {
        return this.stockDailyHistoryRepository.findAllSymbols();
    }

    public StockDailyHistory getLatestExistingRecordBySymbol(String symbol) {
        return this.stockDailyHistoryRepository.findTopBySymbolOrderByTradeDateDesc(symbol);
    }

    public List<StockDailyHistory> getLatestTwoDaysRecordBySymbol(String symbol) {
        return this.stockDailyHistoryRepository.findTop2BySymbolOrderByTradeDateDesc(symbol);
    }

    public LocalDate getEarliestDateBySymbol(String symbol) {
        return this.stockDailyHistoryRepository.findEarliestDate(symbol);
    }

    public LocalDate getLatestDateBySymbol(String symbol) {
        return this.stockDailyHistoryRepository.findLatestDate(symbol);
    }

    public List<StockDailyHistory> getHistoryBySymbol(String symbol) {
        return this.stockDailyHistoryRepository.findBySymbolOrderByTradeDateAsc(symbol);
    }

    public List<StockDailyHistory> getHistoryBySymbolsOnDate(List<String> symbols, LocalDate date) {
        return this.stockDailyHistoryRepository.findBySymbolInAndTradeDate(symbols, date);
    }

    public Map<String, List<StockDailyHistory>> getHistoryBySymbols(List<String> symbols) {
        List<StockDailyHistory> stockHistory = this.stockDailyHistoryRepository.findBySymbolInOrderBySymbolAscTradeDateAsc(symbols);
        return stockHistory.stream().collect(Collectors.groupingBy(StockDailyHistory::getSymbol));
    }

    public Map<String, List<StockDailyHistory>> getHistoryBySymbolsAndDateRange(List<String> symbols, LocalDate start, LocalDate end) {
        List<StockDailyHistory> stockHistory = this.stockDailyHistoryRepository.findBySymbolInAndTradeDateBetweenOrderBySymbolAscTradeDateAsc(symbols, start, end);
        return stockHistory.stream().collect(Collectors.groupingBy(StockDailyHistory::getSymbol));
    }

    public Map<String, List<StockDailyHistory>> getHistoryBySymbolsAfterDate(List<String> symbols, LocalDate start) {
        List<StockDailyHistory> stockHistory = this.stockDailyHistoryRepository.findBySymbolInAndTradeDateGreaterThanEqualOrderBySymbolAscTradeDateAsc(symbols, start);
        return stockHistory.stream().collect(Collectors.groupingBy(StockDailyHistory::getSymbol));
    }

    @Transactional
    public long deleteBySymbolOnDate(String symbol, LocalDate date) {
        return this.stockDailyHistoryRepository.deleteBySymbolAndTradeDate(symbol, date);
    }

    @Transactional
    public void deleteBySymbol(String symbol) {
        this.stockDailyHistoryRepository.deleteBySymbol(symbol);
    }

    @Transactional
    public long deleteBySymbols(List<String> symbols) {
        return this.stockDailyHistoryRepository.deleteBySymbols(symbols);
    }

    @Transactional
    public void updateStockDailyHistoryDatabase(Map<String, List<StockDailyHistory>> stockHistoryBySymbolFromApi) {
        List<StockDailyHistory> stockHistoryToAdd = new ArrayList<>();
        List<StockDailyHistory> stockHistoryToBeDeletedFromDB = new ArrayList<>();

        List<String> existingSymbolsFromDB = this.stockDailyHistoryRepository.findAllSymbols();
        List<String> symbolsNeedFullRewrite = stockHistoryBySymbolFromApi.keySet().stream()
                .filter(sym -> !existingSymbolsFromDB.contains(sym))
                .collect(Collectors.toList());
        List<String> symbolsNoNeedToUpdate = new ArrayList<>();

        Map<String, List<StockDailyHistory>> latestHistoryForEachSymbolFromDB = stockHistoryBySymbolFromApi.keySet().stream()
                .filter(sym -> !symbolsNeedFullRewrite.contains(sym))
                .collect(Collectors.toMap(sym -> sym, this.stockDailyHistoryRepository::findTop2BySymbolOrderByTradeDateDesc));

        // check which symbols and records need to be updated
        latestHistoryForEachSymbolFromDB.keySet().forEach(symbol -> {
            StockDailyHistory latestHistoryFromDB = latestHistoryForEachSymbolFromDB.get(symbol).get(0);
            StockDailyHistory secondLatestHistoryFromDB = latestHistoryForEachSymbolFromDB.get(symbol).size() > 1 ? latestHistoryForEachSymbolFromDB.get(symbol).get(1) : null;
            List<StockDailyHistory> newDataForSymbol = stockHistoryBySymbolFromApi.get(symbol);
            StockDailyHistory latestHistoryFromApi = newDataForSymbol.get(newDataForSymbol.size() - 1);
            StockDailyHistory secondLatestHistoryFromApi = newDataForSymbol.size() >= 2 ? newDataForSymbol.get(newDataForSymbol.size() - 2) : null;

            if (latestHistoryFromDB.equalsByValue(latestHistoryFromApi)) {
                // if latest from API is same as latest in DB, then no need to update
                symbolsNoNeedToUpdate.add(symbol);
            } else if (secondLatestHistoryFromApi != null && latestHistoryFromDB.equalsByValue(secondLatestHistoryFromApi)) {
                // if second latest from API is same as latest in DB, this means current day's data is not updated yet, add latest from API to database
                stockHistoryToAdd.add(latestHistoryFromApi);
            } else if (secondLatestHistoryFromDB != null && secondLatestHistoryFromApi != null && secondLatestHistoryFromDB.equalsByValue(secondLatestHistoryFromApi)) {
                // if second latest from API is same as from DB but latest is different, this means current day's data has changed (maybe updated middle of day)
                // so delete existing latest from DB and add latest from API
                stockHistoryToBeDeletedFromDB.add(latestHistoryFromDB);
                stockHistoryToAdd.add(latestHistoryFromApi);
            } else {
                // need to fully rewrite
                symbolsNeedFullRewrite.add(symbol);
            }
        });

        logger.info("No need to update stock daily history for symbol: {}", StringUtils.join(symbolsNoNeedToUpdate, ","));
        logger.info("Symbols need fully rewrite: {}.", StringUtils.join(symbolsNeedFullRewrite, ","));
        logger.info("Symbols with most recent data to be updated: {}.", StringUtils.join(stockHistoryToAdd.stream().map(StockDailyHistory::getSymbol).distinct().toList(), ","));
        symbolsNeedFullRewrite.forEach(sym -> stockHistoryToAdd.addAll(stockHistoryBySymbolFromApi.get(sym)));
        // running transactions
        long numDeletedRecords = 0;
        numDeletedRecords += stockHistoryToBeDeletedFromDB.size();
        this.stockDailyHistoryRepository.deleteAll(stockHistoryToBeDeletedFromDB);
        long numDeletedBySymbols = this.stockDailyHistoryRepository.deleteBySymbols(symbolsNeedFullRewrite);
        numDeletedRecords += numDeletedBySymbols;
        logger.info("Deleted {} rows from StockDailyHistory where {} is from fully rewritiing.", numDeletedRecords, numDeletedBySymbols);
        logger.info("Number of records to be added: {}.", stockHistoryToAdd.size());
        this.stockDailyHistoryRepository.saveAll(stockHistoryToAdd);
        logger.info("Daily history updated. Symbols: {}", StringUtils.join(stockHistoryBySymbolFromApi.keySet(), ","));
    }
}
