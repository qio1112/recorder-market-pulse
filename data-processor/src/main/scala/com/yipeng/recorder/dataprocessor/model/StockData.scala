package com.yipeng.recorder.dataprocessor.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Local StockData model for the data-processor module
 * This removes dependency on the stock-api-service model
 */
case class StockData(
  @JsonProperty("symbol") symbol: String,
  @JsonProperty("price") price: BigDecimal,
  @JsonProperty("change") change: Option[BigDecimal],
  @JsonProperty("changePercent") changePercent: Option[BigDecimal],
  @JsonProperty("open") open: Option[BigDecimal],
  @JsonProperty("high") high: Option[BigDecimal],
  @JsonProperty("low") low: Option[BigDecimal],
  @JsonProperty("previousClose") previousClose: Option[BigDecimal],
  @JsonProperty("volume") volume: Option[Long],
  @JsonProperty("timestamp") timestamp: LocalDateTime
) 