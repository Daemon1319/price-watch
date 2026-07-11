package com.allan.price_watch.scraper;

import java.math.BigDecimal;

import com.allan.price_watch.product.entity.StockStatus;

/**
 * The output of a single successful scrape — everything {@code ProductService}
 * needs to save/update a {@code Product}. {@code name} may be {@code null}
 * if a {@code Scraper} implementation can extract price/stock but not a
 * clean title — that's an acceptable partial success, not a failure worth
 * throwing {@code ScrapeFailedException} over.
 */
public record ScrapeResult(
    String name,
    BigDecimal price,
    StockStatus stockStatus,
    String thumbnailUrl) {
}