package com.allan.price_watch.product.entity;

/**
 * Stock state as last observed by a scrape. {@code UNKNOWN} covers both "not
 * checked yet" (a brand-new product) and "the scraper couldn't determine
 * stock state from the page" — deliberately not split into two separate
 * values, since neither case should trigger a restock notification.
 */
public enum StockStatus {
  IN_STOCK,
  OUT_OF_STOCK,
  UNKNOWN
}