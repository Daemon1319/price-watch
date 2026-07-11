package com.allan.price_watch.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Dashboard summary payload for the authenticated user. */
public record DashboardSummaryResponse(
    long totalTrackedItems,
    List<PriceDropEntry> recentPriceDrops,
    long unhealthyCount) {

  /** One recent price-drop row on the dashboard summary. */
  public record PriceDropEntry(
      UUID trackedItemId,
      String productName,
      BigDecimal oldPrice,
      BigDecimal newPrice,
      Instant changedAt) {
  }
}