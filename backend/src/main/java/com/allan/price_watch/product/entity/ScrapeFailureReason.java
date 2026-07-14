package com.allan.price_watch.product.entity;

/**
 * Why the last scrape failed. Permanent reasons park the product immediately;
 * transient ones increment {@code consecutive_failures} toward the unhealthy threshold.
 */
public enum ScrapeFailureReason {
  /** Connection timeout, DNS, TLS, etc. */
  NETWORK,
  /** Unexpected client error from the storefront API (not 404). */
  HTTP_4XX,
  /** Upstream 5xx / overload. */
  HTTP_5XX,
  /** Color+size SKU no longer sold (product page may still exist). */
  VARIANT_MISSING,
  /** Product id gone / 404 from commerce API. */
  PRODUCT_UNAVAILABLE,
  /** Response body could not be parsed. */
  PARSE,
  /** Catch-all. */
  UNKNOWN;

  /** True when retries will not help until the user changes SKU or Uniqlo relists. */
  public boolean isPermanent() {
    return this == VARIANT_MISSING || this == PRODUCT_UNAVAILABLE;
  }
}
