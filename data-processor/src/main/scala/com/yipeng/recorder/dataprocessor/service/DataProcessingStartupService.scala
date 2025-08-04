package com.yipeng.recorder.dataprocessor.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class DataProcessingStartupService @Autowired()(
  @Autowired val stockDataConsumerService: StockDataConsumerService,
  @Autowired val sparkStreamingService: SparkStreamingService
) {
  private val logger = LoggerFactory.getLogger(classOf[DataProcessingStartupService])

  /**
   * Start the data processing pipeline when the application is ready
   */
  @EventListener(Array(classOf[ApplicationReadyEvent]))
  def startDataProcessing(): Unit = {
    logger.info("Application is ready, starting data processing pipeline...")
    try {
      // Start the stock data consumer
      stockDataConsumerService.startWebSocketConsumer()
      logger.info("Stock data consumer started successfully")
      // Start Spark Streaming processing
      sparkStreamingService.startStreaming()
      logger.info("Spark Streaming processing started successfully")
      logger.info("Data processing pipeline started successfully")
    } catch {
      case e: Exception =>
        logger.error("Failed to start data processing pipeline", e)
    }
  }
} 