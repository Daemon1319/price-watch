package com.allan.price_watch.scraper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allan.price_watch.notification.entity.OutboxEvent;
import com.allan.price_watch.notification.entity.OutboxEventType;
import com.allan.price_watch.notification.repository.OutboxEventRepository;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.StockStatus;
import com.allan.price_watch.product.repository.ProductRepository;
import com.allan.price_watch.trackeditem.entity.PriceHistory;
import com.allan.price_watch.trackeditem.repository.PriceHistoryRepository;

/**
 * Split out from {@code ScrapeWorker} specifically so {@code @Transactional}
 * actually works — Spring's transaction advice is proxy-based, and calling
 * a {@code @Transactional} method from within the same class instance
 * bypasses the proxy entirely (a well-known Spring gotcha). Putting the
 * persistence logic in its own injected bean means {@code ScrapeWorker}
 * calls through the real proxy, so the transaction boundary is honored.
 *
 * <p>This is also where the transactional outbox pattern's core guarantee
 * actually lives: {@code Product} update, {@code PriceHistory} insert, and
 * {@code OutboxEvent} insert either all commit together or none do. If
 * that weren't atomic, a crash between "update the price" and "record the
 * event" could silently drop a notification a user was relying on.
 *
 * <p>Only emits {@code PRICE_DROP} and {@code RESTOCK} events — not
 * {@code PRICE_INCREASE} or {@code OUT_OF_STOCK} — since nothing
 * downstream acts on those yet (v1 scope, per plan §14). Each event
 * covers the product as a whole; fanning out to the specific users
 * tracking it and filtering by their individual threshold/restock-only
 * preference is {@code NotificationWorker}'s job, not this class's.
 */
@Service
public class ScrapeResultService {

  private final ProductRepository productRepository;
  private final PriceHistoryRepository priceHistoryRepository;
  private final OutboxEventRepository outboxEventRepository;

  public ScrapeResultService(
      ProductRepository productRepository,
      PriceHistoryRepository priceHistoryRepository,
      OutboxEventRepository outboxEventRepository) {
    this.productRepository = productRepository;
    this.priceHistoryRepository = priceHistoryRepository;
    this.outboxEventRepository = outboxEventRepository;
  }

  @Transactional
  public void recordSuccess(Product product, ScrapeResult result) {
    BigDecimal oldPrice = product.getLastKnownPrice();
    StockStatus oldStock = product.getLastKnownStockStatus();

    boolean priceChanged = oldPrice != null && result.price() != null
        && oldPrice.compareTo(result.price()) != 0;
    boolean stockChanged = oldStock != result.stockStatus();

    if (result.name() != null) {
      product.setName(result.name());
    }
    if (result.thumbnailUrl() != null) {
      product.setThumbnailUrl(result.thumbnailUrl());
    }
    product.setLastKnownPrice(result.price());
    product.setLastKnownStockStatus(result.stockStatus());
    product.setLastCheckedAt(Instant.now());
    product.setConsecutiveFailures(0);
    productRepository.save(product);

    // Only insert a history row when something actually changed — not on
    // every routine check — otherwise this table fills with identical
    // repeated values for every unchanged product on every scheduler run.
    if (priceChanged || stockChanged) {
      priceHistoryRepository.save(PriceHistory.builder()
          .product(product)
          .price(result.price())
          .stockStatus(result.stockStatus())
          .build());
    }

    if (priceChanged && result.price().compareTo(oldPrice) < 0) {
      outboxEventRepository.save(OutboxEvent.builder()
          .product(product)
          .eventType(OutboxEventType.PRICE_DROP)
          .payload(Map.of("oldPrice", oldPrice, "newPrice", result.price()))
          .build());
    }

    boolean restocked = oldStock != StockStatus.IN_STOCK && result.stockStatus() == StockStatus.IN_STOCK;
    if (restocked) {
      outboxEventRepository.save(OutboxEvent.builder()
          .product(product)
          .eventType(OutboxEventType.RESTOCK)
          .payload(Map.of())
          .build());
    }
  }

  @Transactional
  public void recordFailure(Product product) {
    product.setConsecutiveFailures(product.getConsecutiveFailures() + 1);
    product.setLastCheckedAt(Instant.now());
    productRepository.save(product);
  }
}