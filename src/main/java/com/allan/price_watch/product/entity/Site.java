package com.allan.price_watch.product.entity;

/**
 * Supported storefronts. Each constant maps 1:1 to a {@code Scraper}
 * under {@code scraper/site/}.
 *
 * <p>To add a site later:
 * <ol>
 *   <li>Add a constant here (e.g. {@code HM}, {@code SHOPEE})</li>
 *   <li>Implement {@code Scraper} for that site and register as a Spring bean</li>
 * </ol>
 * No other packages need to change — {@code ScraperFactory} auto-discovers scrapers.
 */
public enum Site {
  UNIQLO
  // Planned later: HM, SHOPEE, LAZADA, ...
}
