package com.allan.price_watch.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.allan.price_watch.product.entity.StockStatus;
import com.allan.price_watch.trackeditem.entity.PriceHistory;

/** One price/stock history point for API responses. */
public record PriceHistoryResponse(
    BigDecimal price,
    StockStatus stockStatus,
    Instant recordedAt) {

  public static PriceHistoryResponse from(PriceHistory priceHistory) {
    return new PriceHistoryResponse(
        priceHistory.getPrice(), priceHistory.getStockStatus(), priceHistory.getRecordedAt());
  }
}