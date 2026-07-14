package com.allan.price_watch.product;

import com.allan.price_watch.product.entity.ScrapeFailureReason;

/** Shared threshold for when a product is considered unhealthy for scraping. */
public final class ProductHealth {

  public static final int UNHEALTHY_FAILURE_THRESHOLD = 5;

  private ProductHealth() {
  }

  /** True if consecutive failures are still below the unhealthy cutoff. */
  public static boolean isHealthy(int consecutiveFailures) {
    return consecutiveFailures < UNHEALTHY_FAILURE_THRESHOLD;
  }

  /**
   * How much to add to {@code consecutive_failures} for this reason.
   * Permanent reasons jump straight to the unhealthy threshold so the scheduler stops.
   */
  public static int failureIncrement(ScrapeFailureReason reason) {
    if (reason != null && reason.isPermanent()) {
      return UNHEALTHY_FAILURE_THRESHOLD;
    }
    return 1;
  }
}
