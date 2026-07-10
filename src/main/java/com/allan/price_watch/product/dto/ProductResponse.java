package com.allan.price_watch.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.Site;
import com.allan.price_watch.product.entity.StockStatus;

public record ProductResponse(
    UUID id,
    String name,
    String url,
    Site site,
    BigDecimal lastKnownPrice,
    StockStatus lastKnownStockStatus,
    String thumbnailUrl,
    Instant lastCheckedAt,
    boolean healthy) {

  /**
   * Matches the {@code consecutiveFailures >= 5} threshold used by the
   * partial index in {@code V1__init_schema.sql} and
   * {@code ProductRepository.countByConsecutiveFailuresGreaterThanEqual}.
   * The number "5" living in three places instead of one shared constant
   * is a known small wart — acceptable at this scope, but worth
   * consolidating if a fourth place ever needs it.
   */
  private static final int UNHEALTHY_THRESHOLD = 5;

  public static ProductResponse from(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getName(),
        product.getNormalizedUrl(),
        product.getSite(),
        product.getLastKnownPrice(),
        product.getLastKnownStockStatus(),
        product.getThumbnailUrl(),
        product.getLastCheckedAt(),
        product.getConsecutiveFailures() < UNHEALTHY_THRESHOLD);
  }
}