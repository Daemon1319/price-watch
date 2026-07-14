package com.allan.price_watch.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.allan.price_watch.scraper.ProductCheckService;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/** Periodically enqueues scrape jobs for actively tracked healthy products. */
@Component
public class ProductCheckScheduler {

  private final ProductCheckService productCheckService;

  public ProductCheckScheduler(ProductCheckService productCheckService) {
    this.productCheckService = productCheckService;
  }

  /** Publishes one product.check message per product that needs a recheck. */
  @Scheduled(cron = "${app.scheduler.product-check-cron:0 0 */4 * * *}")
  @SchedulerLock(name = "productCheckScheduler", lockAtLeastFor = "PT30S", lockAtMostFor = "PT10M")
  public void enqueueChecks() {
    productCheckService.enqueueAllHealthyActive();
  }
}
