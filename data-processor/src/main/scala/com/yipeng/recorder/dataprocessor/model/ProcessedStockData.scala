package com.yipeng.recorder.dataprocessor.model

import jakarta.persistence._
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Single ProcessedStockData case class that serves as both JPA entity and Spark record
 * This eliminates the need for separate Entity and Record classes
 */
@Entity
@Table(name = "processed_stock_data", indexes = Array(
  new Index(name = "idx_symbol", columnList = "symbol"),
  new Index(name = "idx_symbol_timestamp", columnList = "symbol, timestamp DESC")
))
case class ProcessedStockData(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  id: Option[Long] = None,
  
  // Original StockData columns
  @Column(name = "symbol", nullable = false)
  symbol: String,
  
  @Column(name = "price", precision = 10, scale = 2)
  price: BigDecimal,
  
  @Column(name = "change", precision = 10, scale = 2)
  change: Option[BigDecimal],
  
  @Column(name = "change_percent", precision = 5, scale = 2)
  changePercent: Option[BigDecimal],
  
  @Column(name = "open", precision = 10, scale = 2)
  open: Option[BigDecimal],
  
  @Column(name = "high", precision = 10, scale = 2)
  high: Option[BigDecimal],
  
  @Column(name = "low", precision = 10, scale = 2)
  low: Option[BigDecimal],
  
  @Column(name = "previous_close", precision = 10, scale = 2)
  previousClose: Option[BigDecimal],
  
  @Column(name = "volume")
  volume: Option[Long],
  
  @Column(name = "timestamp", nullable = false)
  timestamp: LocalDateTime,
  
  // Processed/transformed data stored as JSON
  @Column(name = "processed_metrics", columnDefinition = "TEXT")
  processedMetrics: String, // JSON string containing all processed/transformed metrics
  
  @Column(name = "data_points_count")
  dataPointsCount: Int
) {
  // Default constructor for JPA
  def this() = this(None, "", BigDecimal.ZERO, None, None, None, None, None, None, None, LocalDateTime.now(), "", 0)
  
  /**
   * Create a copy with a new ID (useful for database operations)
   */
  def withId(newId: Long): ProcessedStockData = copy(id = Some(newId))
  
  /**
   * Create a copy without ID (useful for creating new records)
   */
  def withoutId: ProcessedStockData = copy(id = None)
} 