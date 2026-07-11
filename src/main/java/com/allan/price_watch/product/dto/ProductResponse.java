package com.allan.price_watch.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.allan.price_watch.product.ProductHealth;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.Site;
import com.allan.price_watch.product.entity.StockStatus;

/** Product API response with latest price/stock and health flag. */
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
        ProductHealth.isHealthy(product.getConsecutiveFailures()));
  }
}