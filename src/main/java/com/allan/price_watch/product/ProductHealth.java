package com.allan.price_watch.product;

/**
 * Shared health thresholds for products. Scrapes increment
 * {@code consecutive_failures}; once the count reaches this threshold the
 * product is treated as UNHEALTHY — the scheduler stops enqueueing checks
 * until something (manual ops / future admin endpoint) resets the counter.
 */
public final class ProductHealth {

  public static final int UNHEALTHY_FAILURE_THRESHOLD = 5;

  private ProductHealth() {
  }

  public static boolean isHealthy(int consecutiveFailures) {
    return consecutiveFailures < UNHEALTHY_FAILURE_THRESHOLD;
  }
}
