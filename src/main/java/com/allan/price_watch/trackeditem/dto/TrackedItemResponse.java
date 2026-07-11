package com.allan.price_watch.trackeditem.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.Site;
import com.allan.price_watch.product.entity.StockStatus;
import com.allan.price_watch.trackeditem.entity.TrackedItem;
import com.allan.price_watch.trackeditem.entity.TrackedItemStatus;

/**
 * Flattens {@code TrackedItem} + its {@code Product} into one response.
 * Prefer {@link #from(TrackedItem, Product)} when you already have a fully
 * loaded product (avoids LazyInitializationException after scrape TX).
 */
public record TrackedItemResponse(
    UUID id,
    UUID productId,
    String productName,
    String url,
    Site site,
    BigDecimal lastKnownPrice,
    StockStatus lastKnownStockStatus,
    String thumbnailUrl,
    BigDecimal priceThreshold,
    boolean notifyOnRestockOnly,
    TrackedItemStatus status,
    Instant createdAt) {

  public static TrackedItemResponse from(TrackedItem trackedItem, Product product) {
    return new TrackedItemResponse(
        trackedItem.getId(),
        product.getId(),
        product.getName(),
        product.getNormalizedUrl(),
        product.getSite(),
        product.getLastKnownPrice(),
        product.getLastKnownStockStatus(),
        product.getThumbnailUrl(),
        trackedItem.getPriceThreshold(),
        trackedItem.isNotifyOnRestockOnly(),
        trackedItem.getStatus(),
        trackedItem.getCreatedAt());
  }

  public static TrackedItemResponse from(TrackedItem trackedItem) {
    return from(trackedItem, trackedItem.getProduct());
  }
}
