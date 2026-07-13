package com.allan.price_watch.product;

/** Shared threshold for when a product is considered unhealthy for scraping. */
public final class ProductHealth {

  public static final int UNHEALTHY_FAILURE_THRESHOLD = 5;

  private ProductHealth() {
  }

  /** True if consecutive failures are still below the unhealthy cutoff. */
  public static boolean isHealthy(int consecutiveFailures) {
    return consecutiveFailures < UNHEALTHY_FAILURE_THRESHOLD;
  }
}
