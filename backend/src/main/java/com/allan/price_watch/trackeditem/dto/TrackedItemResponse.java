package com.allan.price_watch.trackeditem.dto;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.Site;
import com.allan.price_watch.product.entity.StockStatus;
import com.allan.price_watch.trackeditem.entity.TrackedItem;
import com.allan.price_watch.trackeditem.entity.TrackedItemStatus;

/** API view of a tracked item with embedded product + variant fields. */
public record TrackedItemResponse(
    UUID id,
    UUID productId,
    String productName,
    String url,
    Site site,
    BigDecimal lastKnownPrice,
    StockStatus lastKnownStockStatus,
    String thumbnailUrl,
    String colorCode,
    String colorName,
    String sizeCode,
    String sizeName,
    BigDecimal priceThreshold,
    boolean notifyOnRestockOnly,
    TrackedItemStatus status,
    Instant createdAt) {

  public static TrackedItemResponse from(TrackedItem trackedItem, Product product) {
    // Prefer columns; if empty (legacy row), fall back to colorCode/sizeCode on the product URL.
    String colorCode = firstNonBlank(product.getColorCode(), queryParam(product.getNormalizedUrl(), "colorCode"));
    String sizeCode = firstNonBlank(product.getSizeCode(), queryParam(product.getNormalizedUrl(), "sizeCode"));

    return new TrackedItemResponse(
        trackedItem.getId(),
        product.getId(),
        product.getName(),
        product.getNormalizedUrl(),
        product.getSite(),
        product.getLastKnownPrice(),
        product.getLastKnownStockStatus(),
        product.getThumbnailUrl(),
        colorCode,
        product.getColorName(),
        sizeCode,
        product.getSizeName(),
        trackedItem.getPriceThreshold(),
        trackedItem.isNotifyOnRestockOnly(),
        trackedItem.getStatus(),
        trackedItem.getCreatedAt());
  }

  public static TrackedItemResponse from(TrackedItem trackedItem) {
    return from(trackedItem, trackedItem.getProduct());
  }

  private static String firstNonBlank(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    if (fallback != null && !fallback.isBlank()) {
      return fallback;
    }
    return null;
  }

  private static String queryParam(String url, String name) {
    if (url == null || url.isBlank()) {
      return null;
    }
    try {
      String query = URI.create(url).getRawQuery();
      if (query == null || query.isBlank()) {
        return null;
      }
      for (String part : query.split("&")) {
        int eq = part.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
        if (name.equalsIgnoreCase(key)) {
          String value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
          return value.isBlank() ? null : value;
        }
      }
    } catch (IllegalArgumentException ignored) {
      return null;
    }
    return null;
  }
}
