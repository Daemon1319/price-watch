package com.allan.price_watch.scraper;

import java.math.BigDecimal;

import com.allan.price_watch.product.entity.StockStatus;

/** Snapshot of product fields extracted by a successful scrape. */
public record ScrapeResult(
    String name,
    BigDecimal price,
    StockStatus stockStatus,
    String thumbnailUrl) {
}
