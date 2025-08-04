package com.yipeng.recorder.dataprocessor.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.yipeng.recorder.dataprocessor.model.StockData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.stereotype.Service
import org.springframework.web.socket.{CloseStatus, TextMessage, WebSocketSession}
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.client.RestTemplate

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import scala.jdk.CollectionConverters._

@Service
class StockDataConsumerService @Autowired()(
  @Value("${stock.api.websocket.url:ws://localhost:8081/ws/stock-data}") val webSocketUrl: String,
  @Value("${stock.api.rest.url:http://localhost:8081/api/stock-data}") val restApiUrl: String,
  @Value("${kafka.bootstrap-servers}") val kafkaBootstrapServers: String,
  @Value("${kafka.topics.stock-data}") val stockDataTopic: String
) {
  private val logger = LoggerFactory.getLogger(classOf[StockDataConsumerService])
  private val objectMapper = new ObjectMapper()
  private val isRunning = new AtomicBoolean(false)
  private var webSocketSession: WebSocketSession = _
  private var scheduledExecutor: ScheduledExecutorService = _
  private val kafkaProducer = new com.yipeng.recorder.common.kafka.StockProducer(kafkaBootstrapServers, stockDataTopic)
  
  objectMapper.findAndRegisterModules()
  
  /**
   * Start WebSocket consumer
   */
  def startWebSocketConsumer(): Unit = {
    if (isRunning.compareAndSet(false, true)) {
      try {
        val client = new StandardWebSocketClient()
        val handler = new TextWebSocketHandler {
          override def handleTextMessage(session: WebSocketSession, message: TextMessage): Unit = {
            try {
              val stockData = objectMapper.readValue(message.getPayload, classOf[StockData])
              kafkaProducer.send(stockData.symbol, message.getPayload)
              logger.debug(s"Sent stock data to Kafka: ${stockData.symbol}")
            } catch {
              case e: Exception =>
                logger.error("Error processing WebSocket message", e)
            }
          }
          
          override def afterConnectionEstablished(session: WebSocketSession): Unit = {
            logger.info("WebSocket connection established")
            webSocketSession = session
          }
          
          override def afterConnectionClosed(session: WebSocketSession, status: CloseStatus): Unit = {
            logger.info(s"WebSocket connection closed: ${status}")
            webSocketSession = null
          }
        }
        
        client.doHandshake(handler, null, webSocketUrl)
        logger.info("WebSocket consumer started")
      } catch {
        case e: Exception =>
          logger.error("Failed to start WebSocket consumer", e)
          isRunning.set(false)
      }
    }
  }
  
  /**
   * Start REST API consumer (polling)
   */
  def startRestConsumer(): Unit = {
    if (isRunning.compareAndSet(false, true)) {
      scheduledExecutor = Executors.newScheduledThreadPool(1)
      val restTemplate = new RestTemplate()
      
      scheduledExecutor.scheduleAtFixedRate(() => {
        try {
          val response = restTemplate.getForObject(restApiUrl, classOf[String])
          if (response != null) {
            val stockData = objectMapper.readValue(response, classOf[StockData])
            kafkaProducer.send(stockData.symbol, response)
            logger.debug(s"Sent stock data to Kafka via REST: ${stockData.symbol}")
          }
        } catch {
          case e: Exception =>
            logger.error("Error polling REST API", e)
        }
      }, 0, 5, TimeUnit.SECONDS)
      
      logger.info("REST consumer started")
    }
  }
  
  /**
   * Stop consumer
   */
  def stopConsumer(): Unit = {
    if (isRunning.compareAndSet(true, false)) {
      if (webSocketSession != null && webSocketSession.isOpen) {
        webSocketSession.close()
        webSocketSession = null
      }
      
      if (scheduledExecutor != null) {
        scheduledExecutor.shutdown()
        scheduledExecutor = null
      }
      
      kafkaProducer.close()
      logger.info("Consumer stopped")
    }
  }
  
  /**
   * Check if consumer is running
   */
  def isRunningStatus: Boolean = isRunning.get()
} 