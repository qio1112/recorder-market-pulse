package com.yipeng.recorder.dataprocessor.service

import com.yipeng.recorder.dataprocessor.model.ProcessedStockData
import com.yipeng.recorder.dataprocessor.repository.ProcessedStockDataRepository
import com.yipeng.recorder.dataprocessor.spark.SparkStreamingProcessor
import org.apache.spark.streaming.StreamingContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.stereotype.Service

import jakarta.annotation.{PostConstruct, PreDestroy}
import java.util.concurrent.atomic.AtomicBoolean

@Service
class SparkStreamingService @Autowired()(
  @Autowired val streamingContext: StreamingContext,
  @Autowired val repository: ProcessedStockDataRepository,
  @Value("${kafka.bootstrap-servers}") val kafkaBootstrapServers: String,
  @Value("${kafka.topics.stock-data}") val stockDataTopic: String,
  @Value("${kafka.consumer.group-id}") val consumerGroupId: String,
  @Value("${processing.moving-average.window-size}") val windowSize: Int
) {
  private val logger = LoggerFactory.getLogger(classOf[SparkStreamingService])
  private var processor: SparkStreamingProcessor = _
  private val isRunning = new AtomicBoolean(false)
  private var streamingThread: Thread = _

  @PostConstruct
  def init(): Unit = {
    processor = new SparkStreamingProcessor(
      streamingContext,
      kafkaBootstrapServers,
      stockDataTopic,
      consumerGroupId,
      windowSize
    )
  }

  /**
   * Start Spark Streaming processing
   */
  def startStreaming(): Unit = {
    if (isRunning.compareAndSet(false, true)) {
      streamingThread = new Thread(new Runnable {
        override def run(): Unit = runStreaming()
      })
      streamingThread.setDaemon(true)
      streamingThread.start()
      logger.info("Started Spark Streaming processing")
    }
  }

  /**
   * Stop Spark Streaming processing
   */
  def stopStreaming(): Unit = {
    if (isRunning.compareAndSet(true, false)) {
      if (processor != null) {
        processor.stop()
      }
      if (streamingThread != null) {
        streamingThread.interrupt()
      }
      logger.info("Stopped Spark Streaming processing")
    }
  }

  /**
   * Check if streaming is running
   */
  def isRunningStatus: Boolean = isRunning.get()

  /**
   * Main streaming processing logic
   */
  private def runStreaming(): Unit = {
    try {
      // Process the stream using Scala processor
      processor.processStream().foreachRDD { rdd =>
        rdd.foreach { processedData =>
          try {
            // Save directly since ProcessedStockData is now both entity and record
            repository.save(processedData)
            logger.info(s"Saved processed data for symbol: ${processedData.symbol}")
          } catch {
            case e: Exception =>
              logger.error("Error saving processed data", e)
          }
        }
      }
      // Start the streaming context
      processor.start()
      // Await termination (blocking)
      streamingContext.awaitTermination()
    } catch {
      case e: Exception =>
        logger.error("Error in Spark Streaming", e)
        isRunning.set(false)
    }
  }

  @PreDestroy
  def cleanup(): Unit = {
    stopStreaming()
  }
} 