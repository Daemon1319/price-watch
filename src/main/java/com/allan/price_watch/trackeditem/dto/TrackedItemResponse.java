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
 * Flattens {@code TrackedItem} + its {@code Product} into one response
 * shape, matching the GET/POST /tracked-items examples in the REST
 * endpoint reference doc — callers shouldn't need a second request just to
 * see the current price of what they're tracking.
 */
public record TrackedItemResponse(
    UUID id,
    UUID productId,
    String productName,
    String url,
    Site site,
    BigDecimal lastKnownPrice,
    StockStatus lastKnownStockStatus,
    BigDecimal priceThreshold,
    boolean notifyOnRestockOnly,
    TrackedItemStatus status,
    Instant createdAt) {

  public static TrackedItemResponse from(TrackedItem trackedItem) {
    Product product = trackedItem.getProduct();
    return new TrackedItemResponse(
        trackedItem.getId(),
        product.getId(),
        product.getName(),
        product.getNormalizedUrl(),
        product.getSite(),
        product.getLastKnownPrice(),
        product.getLastKnownStockStatus(),
        trackedItem.getPriceThreshold(),
        trackedItem.isNotifyOnRestockOnly(),
        trackedItem.getStatus(),
        trackedItem.getCreatedAt());
  }
}