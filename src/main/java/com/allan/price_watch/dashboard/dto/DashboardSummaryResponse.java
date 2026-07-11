package com.allan.price_watch.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardSummaryResponse(
    long totalTrackedItems,
    List<PriceDropEntry> recentPriceDrops,
    long unhealthyCount) {

  /** One row in {@code recentPriceDrops} — matches the REST endpoint reference doc's example shape. */
  public record PriceDropEntry(
      UUID trackedItemId,
      String productName,
      BigDecimal oldPrice,
      BigDecimal newPrice,
      Instant changedAt) {
  }
}