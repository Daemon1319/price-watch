package com.allan.price_watch.scraper;

import java.net.URI;
import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.repository.ProductRepository;
import com.allan.price_watch.scraper.lock.ProductLockService;
import com.allan.price_watch.scraper.throttle.DomainThrottleService;

import static com.allan.price_watch.config.RabbitMqConfig.PRODUCT_CHECK_QUEUE;

/**
 * Consumes {@code product.check} jobs (one per productId, enqueued by
 * {@code ProductCheckScheduler}) and re-scrapes that product. This is the
 * one place a scrape happens for an <em>existing</em> product — the very
 * first scrape, for a brand new URL, is handled synchronously in
 * {@code ProductService.findOrCreateByUrl} instead, since that caller
 * needs a real result immediately rather than "check back later."
 *
 * <p>Orchestration only: acquire the per-product lock, respect the
 * per-domain throttle, call the resolved {@code Scraper}, then hand the
 * result to {@code ScrapeResultService} for the actual (transactional)
 * persistence — see that class's Javadoc for why the split exists.
 */
@Component
public class ScrapeWorker {

  private static final int MAX_THROTTLE_RETRIES = 3;
  private static final long THROTTLE_RETRY_DELAY_MS = 1000;

  private final ProductRepository productRepository;
  private final ScraperFactory scraperFactory;
  private final ProductLockService productLockService;
  private final DomainThrottleService domainThrottleService;
  private final ScrapeResultService scrapeResultService;

  public ScrapeWorker(
      ProductRepository productRepository,
      ScraperFactory scraperFactory,
      ProductLockService productLockService,
      DomainThrottleService domainThrottleService,
      ScrapeResultService scrapeResultService) {
    this.productRepository = productRepository;
    this.scraperFactory = scraperFactory;
    this.productLockService = productLockService;
    this.domainThrottleService = domainThrottleService;
    this.scrapeResultService = scrapeResultService;
  }

  @RabbitListener(queues = PRODUCT_CHECK_QUEUE)
  public void handle(UUID productId) {
    if (!productLockService.tryLock(productId)) {
      // Another delivery/instance is already scraping this product right
      // now — safe to just drop this one rather than requeue, since it's
      // a routine recheck, not a user-facing action waiting on a result.
      return;
    }

    try {
      process(productId);
    } finally {
      productLockService.unlock(productId);
    }
  }

  private void process(UUID productId) {
    Product product = productRepository.findById(productId).orElse(null);
    if (product == null) {
      // Product was deleted between being enqueued and being processed.
      return;
    }

    URI uri = URI.create(product.getNormalizedUrl());

    if (!awaitThrottleSlot(uri)) {
      // Couldn't get a throttle slot after a few short retries. Rather
      // than block this listener thread indefinitely or hammer the
      // target site, skip this cycle — the next scheduled check picks it
      // back up. Acceptable for a routine recheck; contrast with the
      // first-scrape path in ProductService, which has no such luxury.
      return;
    }

    Scraper scraper = scraperFactory.resolve(uri);

    try {
      ScrapeResult result = scraper.fetch(uri);
      scrapeResultService.recordSuccess(product, result);
    } catch (RuntimeException e) {
      // Covers ScrapeFailedException and anything else a Scraper
      // implementation might throw — a scrape failure is expected,
      // routine behavior (a site's markup changes, a request times out),
      // not something that should crash the listener or dead-letter the
      // message. consecutiveFailures is what escalates a persistently
      // failing product to "unhealthy," not a thrown exception here.
      scrapeResultService.recordFailure(product);
    }
  }

  private boolean awaitThrottleSlot(URI uri) {
    for (int attempt = 0; attempt < MAX_THROTTLE_RETRIES; attempt++) {
      if (domainThrottleService.tryAcquire(uri)) {
        return true;
      }
      try {
        Thread.sleep(THROTTLE_RETRY_DELAY_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }
}