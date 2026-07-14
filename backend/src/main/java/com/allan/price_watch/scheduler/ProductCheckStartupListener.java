package com.allan.price_watch.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.allan.price_watch.scraper.ProductCheckService;

/**
 * After the app is fully up, enqueue scrapes for products that look stale so a
 * restart does not wait for the next wall-clock cron slot.
 */
@Component
public class ProductCheckStartupListener {

  private static final Logger log = LoggerFactory.getLogger(ProductCheckStartupListener.class);

  private final ProductCheckService productCheckService;

  public ProductCheckStartupListener(ProductCheckService productCheckService) {
    this.productCheckService = productCheckService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    try {
      int n = productCheckService.enqueueStaleHealthyActive();
      if (n == 0) {
        log.debug("Startup catch-up: no stale products to enqueue");
      }
    } catch (RuntimeException e) {
      // Do not prevent app start if Rabbit is briefly unavailable.
      log.warn("Startup catch-up failed: {}", e.getMessage());
    }
  }
}
