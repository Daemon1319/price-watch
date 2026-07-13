package com.allan.price_watch.scraper;

import java.math.BigDecimal;

import com.allan.price_watch.product.entity.StockStatus;

/** Snapshot of product fields extracted by a successful scrape. */
public record ScrapeResult(
    String name,
    BigDecimal price,
    StockStatus stockStatus,
    String thumbnailUrl,
    String colorCode,
    String colorName,
    String sizeCode,
    String sizeName) {

  /** Convenience for scrapers that have no variant metadata. */
  public static ScrapeResult of(
      String name, BigDecimal price, StockStatus stockStatus, String thumbnailUrl) {
    return new ScrapeResult(name, price, stockStatus, thumbnailUrl, null, null, null, null);
  }
}
