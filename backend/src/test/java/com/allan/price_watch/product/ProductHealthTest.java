package com.allan.price_watch.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.allan.price_watch.product.entity.ScrapeFailureReason;

class ProductHealthTest {

  @Test
  void healthyBelowThreshold() {
    assertTrue(ProductHealth.isHealthy(0));
    assertTrue(ProductHealth.isHealthy(4));
    assertFalse(ProductHealth.isHealthy(5));
  }

  @Test
  void permanentFailuresJumpToThreshold() {
    assertEquals(
        ProductHealth.UNHEALTHY_FAILURE_THRESHOLD,
        ProductHealth.failureIncrement(ScrapeFailureReason.VARIANT_MISSING));
    assertEquals(
        ProductHealth.UNHEALTHY_FAILURE_THRESHOLD,
        ProductHealth.failureIncrement(ScrapeFailureReason.PRODUCT_UNAVAILABLE));
  }

  @Test
  void transientFailuresIncrementByOne() {
    assertEquals(1, ProductHealth.failureIncrement(ScrapeFailureReason.NETWORK));
    assertEquals(1, ProductHealth.failureIncrement(ScrapeFailureReason.HTTP_5XX));
    assertEquals(1, ProductHealth.failureIncrement(ScrapeFailureReason.PARSE));
  }
}
