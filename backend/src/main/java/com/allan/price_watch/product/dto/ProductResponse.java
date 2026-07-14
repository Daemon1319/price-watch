package com.allan.price_watch.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.allan.price_watch.product.ProductHealth;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.ScrapeFailureReason;
import com.allan.price_watch.product.entity.Site;
import com.allan.price_watch.product.entity.StockStatus;

/** Product API response with latest price/stock, variant, and health flag. */
public record ProductResponse(
    UUID id,
    String name,
    String url,
    Site site,
    BigDecimal lastKnownPrice,
    StockStatus lastKnownStockStatus,
    String thumbnailUrl,
    String colorCode,
    String colorName,
    String sizeCode,
    String sizeName,
    Instant lastCheckedAt,
    boolean healthy,
    ScrapeFailureReason lastFailureReason,
    String lastFailureDetail) {

  public static ProductResponse from(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getName(),
        product.getNormalizedUrl(),
        product.getSite(),
        product.getLastKnownPrice(),
        product.getLastKnownStockStatus(),
        product.getThumbnailUrl(),
        product.getColorCode(),
        product.getColorName(),
        product.getSizeCode(),
        product.getSizeName(),
        product.getLastCheckedAt(),
        ProductHealth.isHealthy(product.getConsecutiveFailures()),
        product.getLastFailureReason(),
        product.getLastFailureDetail());
  }
}