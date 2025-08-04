package com.yipeng.recorder.dataprocessor.model

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Case class representing all processed/transformed metrics for stock data
 * This will be serialized to JSON and stored in the processed_metrics column
 * Note: Original source data (price, volume, etc.) is stored in separate columns
 */
case class StockDataMetrics(
  // Moving averages (calculated from historical data)
  movingAverage1Min: BigDecimal,
  movingAverage5Min: Option[BigDecimal] = None,
  movingAverage15Min: Option[BigDecimal] = None,
  
  // Volume metrics (calculated)
  volumeAverage: Option[Long] = None,
  volumeChange: Option[Long] = None,
  
  // Volatility metrics (calculated)
  volatility: Option[BigDecimal] = None,
  priceRange: Option[BigDecimal] = None,
  standardDeviation: Option[BigDecimal] = None,
  
  // Technical indicators (calculated)
  rsi: Option[BigDecimal] = None,
  macd: Option[BigDecimal] = None,
  bollingerUpper: Option[BigDecimal] = None,
  bollingerLower: Option[BigDecimal] = None,
  
  // Trend indicators (calculated)
  trendDirection: Option[String] = None, // "UP", "DOWN", "SIDEWAYS"
  trendStrength: Option[BigDecimal] = None,
  
  // Momentum indicators (calculated)
  momentum: Option[BigDecimal] = None,
  rateOfChange: Option[BigDecimal] = None,
  
  // Metadata
  processingTimestamp: LocalDateTime,
  windowSize: Int,
  dataPointsUsed: Int,
  
  // Additional custom metrics can be added here without schema changes
  customMetrics: Map[String, String] = Map.empty
) 