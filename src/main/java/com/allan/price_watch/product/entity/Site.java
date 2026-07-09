package com.allan.price_watch.product.entity;

/**
 * Supported storefronts. Adding a new site (e.g. Shopee, per plan §16) means
 * adding a constant here and a matching {@code Scraper} implementation in
 * {@code scraper/site/} — nothing else in the app needs to know it exists.
 */
public enum Site {
  UNIQLO,
  HM
}