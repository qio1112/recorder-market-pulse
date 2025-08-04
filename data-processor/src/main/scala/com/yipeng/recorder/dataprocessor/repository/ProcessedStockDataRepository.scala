package com.yipeng.recorder.dataprocessor.repository

import com.yipeng.recorder.dataprocessor.model.ProcessedStockData
import org.springframework.data.jpa.repository.{JpaRepository, Query}
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import scala.jdk.CollectionConverters._

@Repository
trait ProcessedStockDataRepository extends JpaRepository[ProcessedStockData, Long] {
  
  def findBySymbolOrderByTimestampDesc(symbol: String): java.util.List[ProcessedStockData]
  
  @Query("SELECT p FROM ProcessedStockData p WHERE p.symbol = :symbol AND p.timestamp >= :since ORDER BY p.timestamp DESC")
  def findRecentBySymbol(@Param("symbol") symbol: String, @Param("since") since: LocalDateTime): java.util.List[ProcessedStockData]
  
  @Query("SELECT p FROM ProcessedStockData p WHERE p.timestamp >= :since ORDER BY p.timestamp DESC")
  def findRecentData(@Param("since") since: LocalDateTime): java.util.List[ProcessedStockData]
  
  @Query("SELECT p FROM ProcessedStockData p WHERE (:symbol IS NULL OR p.symbol = :symbol) AND p.timestamp BETWEEN :startTime AND :endTime ORDER BY p.timestamp DESC")
  def findBySymbolAndTimestampBetweenOrderByTimestampDesc(
    @Param("symbol") symbol: String, 
    @Param("startTime") startTime: LocalDateTime, 
    @Param("endTime") endTime: LocalDateTime
  ): java.util.List[ProcessedStockData]
  
  @Query("SELECT p FROM ProcessedStockData p WHERE p.symbol = :symbol ORDER BY p.timestamp DESC LIMIT 1")
  def findLatestBySymbol(@Param("symbol") symbol: String): Option[ProcessedStockData]
  
  @Query("SELECT p FROM ProcessedStockData p WHERE p.symbol = :symbol ORDER BY p.timestamp DESC LIMIT :limit")
  def findLatestBySymbolWithLimit(@Param("symbol") symbol: String, @Param("limit") limit: Int): java.util.List[ProcessedStockData]
  
  @Query("SELECT COUNT(p) FROM ProcessedStockData p WHERE p.symbol = :symbol AND p.timestamp >= :since")
  def countBySymbolSince(@Param("symbol") symbol: String, @Param("since") since: LocalDateTime): Long
  
  @Query("SELECT p FROM ProcessedStockData p WHERE (:symbol IS NULL OR p.symbol = :symbol) AND p.volume >= :volumeThreshold ORDER BY p.timestamp DESC")
  def findHighVolumeData(@Param("symbol") symbol: String, @Param("volumeThreshold") volumeThreshold: Long): java.util.List[ProcessedStockData]
  
  @Query("SELECT DISTINCT p.symbol FROM ProcessedStockData p WHERE p.timestamp >= :since")
  def findActiveSymbols(@Param("since") since: LocalDateTime): java.util.List[String]
} 