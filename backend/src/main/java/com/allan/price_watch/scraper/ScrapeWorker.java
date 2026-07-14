package com.allan.price_watch.scraper;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.allan.price_watch.common.exception.InvalidVariantException;
import com.allan.price_watch.common.exception.ScrapeFailedException;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.ScrapeFailureReason;
import com.allan.price_watch.product.repository.ProductRepository;
import com.allan.price_watch.scraper.lock.ProductLockService;
import com.allan.price_watch.scraper.throttle.DomainThrottleService;
import com.rabbitmq.client.Channel;

import static com.allan.price_watch.config.RabbitMqConfig.PRODUCT_CHECK_QUEUE;

/** Consumes product-check jobs: throttle, lock, scrape, then record the result. */
@Component
public class ScrapeWorker {

  private static final Logger log = LoggerFactory.getLogger(ScrapeWorker.class);

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

  /** Handles one product.check message with manual ACK/NACK. */
  @RabbitListener(queues = PRODUCT_CHECK_QUEUE, ackMode = "MANUAL")
  public void handle(
      UUID productId,
      Channel channel,
      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

    Product product = productRepository.findById(productId).orElse(null);
    if (product == null) {
      channel.basicAck(deliveryTag, false);
      return;
    }

    URI uri = URI.create(product.getNormalizedUrl());

    if (!awaitThrottleSlot(uri)) {
      log.debug("Throttle miss for product {}; requeue", productId);
      channel.basicNack(deliveryTag, false, true);
      return;
    }

    if (!productLockService.tryLock(productId)) {
      // Another worker holds the lock — retry soon.
      channel.basicNack(deliveryTag, false, true);
      return;
    }

    try {
      ProcessOutcome outcome = scrape(product, uri);
      if (outcome == ProcessOutcome.SCRAPE_FAILED) {
        channel.basicNack(deliveryTag, false, false);
      } else {
        channel.basicAck(deliveryTag, false);
      }
    } catch (RuntimeException e) {
      log.warn("Unexpected error scraping product {}: {}", productId, e.getMessage());
      channel.basicNack(deliveryTag, false, false);
    } finally {
      productLockService.unlock(productId);
    }
  }

  /** Runs the site scraper and records success or failure. */
  private ProcessOutcome scrape(Product product, URI uri) {
    Scraper scraper = scraperFactory.resolve(uri);

    try {
      ScrapeResult result = scraper.fetch(uri);
      scrapeResultService.recordSuccess(product, result);
      return ProcessOutcome.SUCCESS;
    } catch (InvalidVariantException e) {
      log.debug("Variant missing for product {}: {}", product.getId(), e.getMessage());
      scrapeResultService.recordFailure(product, ScrapeFailureReason.VARIANT_MISSING, e.getMessage());
      return ProcessOutcome.SCRAPE_FAILED;
    } catch (ScrapeFailedException e) {
      log.debug("Scrape failed for product {}: {}", product.getId(), e.getMessage());
      scrapeResultService.recordFailure(product, e.getReason(), e.getMessage());
      return ProcessOutcome.SCRAPE_FAILED;
    } catch (RuntimeException e) {
      log.debug("Unexpected scrape error for product {}: {}", product.getId(), e.getMessage());
      scrapeResultService.recordFailure(product, ScrapeFailureReason.UNKNOWN, e.getMessage());
      return ProcessOutcome.SCRAPE_FAILED;
    }
  }

  /** Waits briefly for a domain throttle slot before scraping. */
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

  private enum ProcessOutcome {
    SUCCESS,
    SCRAPE_FAILED
  }
}
