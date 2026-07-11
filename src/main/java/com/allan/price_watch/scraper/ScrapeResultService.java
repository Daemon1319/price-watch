package com.allan.price_watch.scraper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allan.price_watch.notification.OutboxPayloadKeys;
import com.allan.price_watch.notification.entity.OutboxEvent;
import com.allan.price_watch.notification.entity.OutboxEventType;
import com.allan.price_watch.notification.repository.OutboxEventRepository;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.StockStatus;
import com.allan.price_watch.product.repository.ProductRepository;
import com.allan.price_watch.trackeditem.entity.PriceHistory;
import com.allan.price_watch.trackeditem.repository.PriceHistoryRepository;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

import io.micrometer.core.instrument.MeterRegistry;

/** Persists scrape outcomes: product update, price history, and outbox events. */
@Service
public class ScrapeResultService {

  private final ProductRepository productRepository;
  private final PriceHistoryRepository priceHistoryRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final TrackedItemRepository trackedItemRepository;
  private final CacheManager cacheManager;
  private final MeterRegistry meterRegistry;

  public ScrapeResultService(
      ProductRepository productRepository,
      PriceHistoryRepository priceHistoryRepository,
      OutboxEventRepository outboxEventRepository,
      TrackedItemRepository trackedItemRepository,
      CacheManager cacheManager,
      MeterRegistry meterRegistry) {
    this.productRepository = productRepository;
    this.priceHistoryRepository = priceHistoryRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.trackedItemRepository = trackedItemRepository;
    this.cacheManager = cacheManager;
    this.meterRegistry = meterRegistry;
  }

  /** Updates product state and enqueues notifications when price/stock change. */
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
    if (result.thumbnailUrl() != null && !result.thumbnailUrl().isBlank()) {
      product.setThumbnailUrl(result.thumbnailUrl());
    }

    product.setLastKnownPrice(result.price());
    product.setLastKnownStockStatus(result.stockStatus());
    product.setLastCheckedAt(Instant.now());
    product.setConsecutiveFailures(0);
    productRepository.save(product);

    if ((priceChanged || stockChanged) && result.price() != null) {
      priceHistoryRepository.save(PriceHistory.builder()
          .product(product)
          .price(result.price())
          .stockStatus(result.stockStatus())
          .build());
    }

    if (priceChanged && result.price() != null && result.price().compareTo(oldPrice) < 0) {
      outboxEventRepository.save(OutboxEvent.builder()
          .product(product)
          .eventType(OutboxEventType.PRICE_DROP)
          .payload(Map.of(
              OutboxPayloadKeys.OLD_PRICE, oldPrice,
              OutboxPayloadKeys.NEW_PRICE, result.price()))
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

    invalidateDashboardCaches(product.getId());
    meterRegistry.counter("scrape.success", "site", product.getSite().name()).increment();
  }

  /** Increments consecutive failures after a scrape error. */
  @Transactional
  public void recordFailure(Product product) {
    product.setConsecutiveFailures(product.getConsecutiveFailures() + 1);
    product.setLastCheckedAt(Instant.now());
    productRepository.save(product);

    invalidateDashboardCaches(product.getId());
    meterRegistry.counter("scrape.failure", "site", product.getSite().name()).increment();
  }

  /** Evicts dashboard summary cache for every user tracking this product. */
  private void invalidateDashboardCaches(UUID productId) {
    Cache cache = cacheManager.getCache("dashboardSummary");
    if (cache == null || productId == null) {
      return;
    }
    List<UUID> userIds = trackedItemRepository.findUserIdsByProductId(productId);
    for (UUID userId : userIds) {
      cache.evict(userId);
    }
  }
}
