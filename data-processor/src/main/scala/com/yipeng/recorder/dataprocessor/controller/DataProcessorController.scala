package com.yipeng.recorder.dataprocessor.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.yipeng.recorder.dataprocessor.model.ProcessedStockData
import com.yipeng.recorder.dataprocessor.repository.ProcessedStockDataRepository
import com.yipeng.recorder.dataprocessor.service.{SparkStreamingService, StockDataConsumerService}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation._

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util
import scala.jdk.CollectionConverters._

@RestController
@RequestMapping(Array("/api/data-processor"))
class DataProcessorController @Autowired()(
  @Autowired val stockDataConsumerService: StockDataConsumerService,
  @Autowired val sparkStreamingService: SparkStreamingService,
  @Autowired val processedStockDataRepository: ProcessedStockDataRepository
) {
  private val objectMapper = new ObjectMapper()

  /**
   * Get the status of the data processing pipeline
   */
  @GetMapping(Array("/status"))
  def getStatus(): ResponseEntity[util.Map[String, Object]] = {
    val status = new util.HashMap[String, Object]()
    status.put("consumerRunning", Boolean.box(stockDataConsumerService.isRunningStatus))
    status.put("streamingRunning", Boolean.box(sparkStreamingService.isRunningStatus))
    status.put("timestamp", LocalDateTime.now())
    ResponseEntity.ok(status)
  }

  /**
   * Start the data processing pipeline
   */
  @PostMapping(Array("/start"))
  def startProcessing(): ResponseEntity[util.Map[String, String]] = {
    val response = new util.HashMap[String, String]()
    try {
      stockDataConsumerService.startWebSocketConsumer()
      sparkStreamingService.startStreaming()
      response.put("status", "success")
      response.put("message", "Data processing pipeline started successfully")
      ResponseEntity.ok(response)
    } catch {
      case e: Exception =>
        response.put("status", "error")
        response.put("message", s"Failed to start data processing pipeline: ${e.getMessage}")
        ResponseEntity.internalServerError().body(response)
    }
  }

  /**
   * Stop the data processing pipeline
   */
  @PostMapping(Array("/stop"))
  def stopProcessing(): ResponseEntity[util.Map[String, String]] = {
    val response = new util.HashMap[String, String]()
    try {
      stockDataConsumerService.stopConsumer()
      sparkStreamingService.stopStreaming()
      response.put("status", "success")
      response.put("message", "Data processing pipeline stopped successfully")
      ResponseEntity.ok(response)
    } catch {
      case e: Exception =>
        response.put("status", "error")
        response.put("message", s"Failed to stop data processing pipeline: ${e.getMessage}")
        ResponseEntity.internalServerError().body(response)
    }
  }

  /**
   * Get recent processed stock data
   */
  @GetMapping(Array("/processed-data"))
  def getProcessedData(
    @RequestParam(required = false) symbol: String,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) since: LocalDateTime,
    @RequestParam(defaultValue = "100") limit: Int
  ): ResponseEntity[util.List[ProcessedStockData]] = {
    val data =
      if (symbol != null && since != null) processedStockDataRepository.findRecentBySymbol(symbol, since)
      else if (symbol != null) processedStockDataRepository.findBySymbolOrderByTimestampDesc(symbol)
      else if (since != null) processedStockDataRepository.findRecentData(since)
      else processedStockDataRepository.findAll()
    val limited = if (data.size() > limit) data.subList(0, limit) else data
    ResponseEntity.ok(limited)
  }

  /**
   * Get processed data for a specific symbol
   */
  @GetMapping(Array("/processed-data/{symbol}"))
  def getProcessedDataBySymbol(
    @PathVariable symbol: String,
    @RequestParam(defaultValue = "100") limit: Int
  ): ResponseEntity[util.List[ProcessedStockData]] = {
    val data = processedStockDataRepository.findBySymbolOrderByTimestampDesc(symbol)
    val limited = if (data.size() > limit) data.subList(0, limit) else data
    ResponseEntity.ok(limited)
  }

  /**
   * Get processed data within a time range
   */
  @GetMapping(Array("/processed-data/range"))
  def getProcessedDataInRange(
    @RequestParam(required = false) symbol: String,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startTime: LocalDateTime,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endTime: LocalDateTime
  ): ResponseEntity[util.List[ProcessedStockData]] = {
    val data =
      if (symbol != null)
        processedStockDataRepository.findBySymbolAndTimestampBetweenOrderByTimestampDesc(symbol, startTime, endTime)
      else
        processedStockDataRepository.findBySymbolAndTimestampBetweenOrderByTimestampDesc(null, startTime, endTime)
    ResponseEntity.ok(data)
  }

  /**
   * Get processed metrics for a specific symbol
   */
  @GetMapping(Array("/processed-data/{symbol}/metrics"))
  def getProcessedMetricsBySymbol(
    @PathVariable symbol: String,
    @RequestParam(defaultValue = "100") limit: Int
  ): ResponseEntity[util.List[util.Map[String, Object]]] = {
    val data = processedStockDataRepository.findBySymbolOrderByTimestampDesc(symbol)
    val limited = if (data.size() > limit) data.subList(0, limit) else data
    val metrics = limited.asScala.map { record =>
      val result: util.Map[String, Object] = new util.HashMap[String, Object]()
      result.put("symbol", record.symbol)
      result.put("timestamp", record.timestamp)
      result.put("price", record.price)
      try {
        val metricsNode = objectMapper.readTree(record.processedMetrics)
        result.put("metrics", metricsNode)
      } catch {
        case _: Exception => result.put("metrics", "{}")
      }
      result
    }.asJava
    ResponseEntity.ok(metrics)
  }

  // ... (other endpoints can be converted similarly as needed) ...
} 