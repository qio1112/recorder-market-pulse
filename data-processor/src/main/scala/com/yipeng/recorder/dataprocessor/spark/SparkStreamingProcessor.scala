package com.yipeng.recorder.dataprocessor.spark

import com.fasterxml.jackson.databind.ObjectMapper
import com.yipeng.recorder.dataprocessor.model.{ProcessedStockData, StockDataMetrics, StockData}
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.slf4j.LoggerFactory

import java.math.{BigDecimal, RoundingMode}
import java.time.LocalDateTime
import scala.collection.JavaConverters._
import scala.math.Ordering
import scala.util.{Failure, Success, Try}

/**
 * Scala-based Spark Streaming processor for real-time stock data analysis
 */
class SparkStreamingProcessor(
  val streamingContext: StreamingContext,
  val kafkaBootstrapServers: String,
  val stockDataTopic: String,
  val consumerGroupId: String,
  val windowSize: Int
) {
  
  private val logger = LoggerFactory.getLogger(classOf[SparkStreamingProcessor])
  private val objectMapper = new ObjectMapper()
  objectMapper.findAndRegisterModules()
  
  // Implicit ordering for BigDecimal
  implicit val bigDecimalOrdering: Ordering[BigDecimal] = Ordering.by(_.doubleValue())
  
  // Implicit Numeric for BigDecimal
  implicit val bigDecimalNumeric: Numeric[BigDecimal] = new Numeric[BigDecimal] {
    def plus(x: BigDecimal, y: BigDecimal): BigDecimal = x.add(y)
    def minus(x: BigDecimal, y: BigDecimal): BigDecimal = x.subtract(y)
    def times(x: BigDecimal, y: BigDecimal): BigDecimal = x.multiply(y)
    def negate(x: BigDecimal): BigDecimal = x.negate()
    def fromInt(x: Int): BigDecimal = BigDecimal.valueOf(x)
    def toInt(x: BigDecimal): Int = x.intValue()
    def toLong(x: BigDecimal): Long = x.longValue()
    def toFloat(x: BigDecimal): Float = x.floatValue()
    def toDouble(x: BigDecimal): Double = x.doubleValue()
    def compare(x: BigDecimal, y: BigDecimal): Int = x.compareTo(y)
    def parseString(str: String): Option[BigDecimal] = {
      try {
        Some(new BigDecimal(str))
      } catch {
        case _: NumberFormatException => None
      }
    }
  }
  
  /**
   * Create Kafka stream
   */
  private def createKafkaStream(): DStream[String] = {
    val kafkaParams = Map[String, Object](
      "bootstrap.servers" -> kafkaBootstrapServers,
      "key.deserializer" -> "org.apache.kafka.common.serialization.StringDeserializer",
      "value.deserializer" -> "org.apache.kafka.common.serialization.StringDeserializer",
      "group.id" -> consumerGroupId,
      "auto.offset.reset" -> "earliest",
      "enable.auto.commit" -> "true"
    )
    
    val topics = Array(stockDataTopic)
    
    KafkaUtils.createDirectStream[String, String](
      streamingContext,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](topics, kafkaParams)
    ).map(_.value())
  }
  
  /**
   * Parse JSON to StockData objects
   */
  private def parseStockData(jsonStream: DStream[String]): DStream[StockData] = {
    jsonStream.flatMap { json =>
      Try(objectMapper.readValue(json, classOf[StockData])) match {
        case Success(stockData) => Some(stockData)
        case Failure(exception) =>
          logger.error(s"Error parsing JSON: $json", exception)
          None
      }
    }
  }
  
  /**
   * Calculate processed metrics from stock data
   */
  private def calculateProcessedMetrics(stockDataList: Iterable[StockData]): StockDataMetrics = {
    val dataList = stockDataList.toList
    
    if (dataList.isEmpty) {
      // Return empty metrics if no data
      StockDataMetrics(
        movingAverage1Min = BigDecimal.ZERO,
        processingTimestamp = LocalDateTime.now(),
        windowSize = windowSize,
        dataPointsUsed = 0
      )
    } else {
      // Sort by timestamp
      val sortedData = dataList.sortBy(_.timestamp)
      val latest = sortedData.last
      
      // Calculate moving average
      val sum = sortedData.map(_.price).foldLeft(BigDecimal.ZERO)(_.add(_))
      val movingAverage1Min = sum.divide(BigDecimal.valueOf(sortedData.size), 2, RoundingMode.HALF_UP)
      
      // Calculate volume average
      val volumes = sortedData.flatMap(data => data.volume)
      val volumeAverage = if (volumes.nonEmpty) Some(volumes.sum / volumes.size) else None
      
      // Calculate price range
      val prices = sortedData.map(_.price)
      val priceRange = if (prices.nonEmpty) {
        Some(prices.max.subtract(prices.min))
      } else None
      
      // Calculate volatility (standard deviation)
      val volatility = if (prices.size > 1) {
        val mean = prices.sum.divide(BigDecimal.valueOf(prices.size), 2, RoundingMode.HALF_UP)
        val variance = prices.map(price => price.subtract(mean).pow(2)).sum
          .divide(BigDecimal.valueOf(prices.size), 2, RoundingMode.HALF_UP)
        Some(BigDecimal.valueOf(Math.sqrt(variance.doubleValue())))
      } else None
      
      // Determine trend direction
      val trendDirection = if (prices.size >= 2) {
        val firstPrice = prices.head
        val lastPrice = prices.last
        if (lastPrice.compareTo(firstPrice) > 0) Some("UP")
        else if (lastPrice.compareTo(firstPrice) < 0) Some("DOWN")
        else Some("SIDEWAYS")
      } else None
      
      // Calculate momentum
      val momentum = if (prices.size >= 2) {
        Some(prices.last.subtract(prices.head))
      } else None
      
      StockDataMetrics(
        movingAverage1Min = movingAverage1Min,
        volumeAverage = volumeAverage,
        priceRange = priceRange,
        volatility = volatility,
        trendDirection = trendDirection,
        momentum = momentum,
        processingTimestamp = LocalDateTime.now(),
        windowSize = windowSize,
        dataPointsUsed = sortedData.size
      )
    }
  }
  
  /**
   * Process the streaming data
   */
  def processStream(): DStream[ProcessedStockData] = {
    // Create Kafka stream
    val stockDataStream = createKafkaStream()
    
    // Parse JSON to StockData objects
    val parsedStream = parseStockData(stockDataStream)
    
    // Group by symbol within each batch using transform
    val groupedStream = parsedStream.transform { rdd =>
      rdd.groupBy(_.symbol)
    }
    
    // Calculate processed data for each symbol group
    groupedStream.flatMap { case (symbol, stockDataIterable) =>
      val stockDataList = stockDataIterable.toList
      
      if (stockDataList.nonEmpty) {
        // Find the latest data point by timestamp
        val latest = stockDataList.sortBy(_.timestamp).last
        val processedMetrics = calculateProcessedMetrics(stockDataList)
        
        // Serialize processed metrics to JSON
        val metricsJson = Try(objectMapper.writeValueAsString(processedMetrics)) match {
          case Success(json) => json
          case Failure(exception) =>
            logger.error("Error serializing metrics to JSON", exception)
            "{}"
        }
        
        Some(ProcessedStockData(
          symbol = symbol,
          price = latest.price,
          change = latest.change,
          changePercent = latest.changePercent,
          open = latest.open,
          high = latest.high,
          low = latest.low,
          previousClose = latest.previousClose,
          volume = latest.volume,
          timestamp = latest.timestamp,
          processedMetrics = metricsJson,
          dataPointsCount = stockDataList.size
        ))
      } else {
        None
      }
    }
  }
  
  /**
   * Start the streaming context
   */
  def start(): Unit = {
    logger.info("Starting Spark Streaming context")
    streamingContext.start()
  }
  
  /**
   * Stop the streaming context
   */
  def stop(): Unit = {
    logger.info("Stopping Spark Streaming context")
    streamingContext.stop()
  }
  
  /**
   * Await termination
   */
  def awaitTermination(): Unit = {
    streamingContext.awaitTermination()
  }
} 