package com.allan.price_watch.dashboard;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.allan.price_watch.dashboard.dto.DashboardSummaryResponse;
import com.allan.price_watch.dashboard.dto.DashboardSummaryResponse.PriceDropEntry;
import com.allan.price_watch.notification.OutboxPayloadKeys;
import com.allan.price_watch.notification.entity.OutboxEvent;
import com.allan.price_watch.notification.repository.OutboxEventRepository;
import com.allan.price_watch.product.ProductHealth;
import com.allan.price_watch.trackeditem.entity.TrackedItem;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

/**
 * Read-through Redis cache for the dashboard summary (key = user id,
 * TTL 10 min via {@code spring.cache.redis.time-to-live}). After each
 * scrape, {@code ScrapeResultService} explicitly evicts the cache entries
 * for every user tracking that product so a price drop shows up on the
 * next dashboard load without waiting for TTL. Track create/update/delete
 * also evict via {@code TrackedItemService}.
 */
@Service
public class DashboardService {

  private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

  private static final int RECENT_PRICE_DROPS_LIMIT = 10;
  private static final Duration RECENT_WINDOW = Duration.ofDays(7);

  private final TrackedItemRepository trackedItemRepository;
  private final OutboxEventRepository outboxEventRepository;

  public DashboardService(
      TrackedItemRepository trackedItemRepository,
      OutboxEventRepository outboxEventRepository) {
    this.trackedItemRepository = trackedItemRepository;
    this.outboxEventRepository = outboxEventRepository;
  }

  @Cacheable(value = "dashboardSummary", key = "#userId")
  public DashboardSummaryResponse getSummary(UUID userId) {
    long totalTrackedItems = trackedItemRepository.countByUserId(userId);

    long unhealthyCount = trackedItemRepository
        .countByUserIdAndProduct_ConsecutiveFailuresGreaterThanEqual(
            userId, ProductHealth.UNHEALTHY_FAILURE_THRESHOLD);

    List<PriceDropEntry> recentPriceDrops = buildRecentPriceDrops(userId);

    return new DashboardSummaryResponse(totalTrackedItems, recentPriceDrops, unhealthyCount);
  }

  private List<PriceDropEntry> buildRecentPriceDrops(UUID userId) {
    Map<UUID, UUID> trackedItemIdByProductId = trackedItemRepository
        .findByUserId(userId, Pageable.unpaged()).stream()
        .collect(Collectors.toMap(ti -> ti.getProduct().getId(), TrackedItem::getId, (a, b) -> a));

    List<OutboxEvent> events = outboxEventRepository.findRecentPriceDropsForUser(
        userId, Instant.now().minus(RECENT_WINDOW), PageRequest.of(0, RECENT_PRICE_DROPS_LIMIT));

    return events.stream()
        .map(event -> toPriceDropEntry(event, trackedItemIdByProductId))
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * Maps an outbox PRICE_DROP row. Bad/missing payload keys are skipped so one
   * corrupt event cannot 500 the whole dashboard. Payload shape is owned by
   * {@link com.allan.price_watch.scraper.ScrapeResultService} via
   * {@link OutboxPayloadKeys}.
   */
  private PriceDropEntry toPriceDropEntry(OutboxEvent event, Map<UUID, UUID> trackedItemIdByProductId) {
    Map<String, Object> payload = event.getPayload();
    if (payload == null) {
      log.warn("Skipping outbox event {} — null payload", event.getId());
      return null;
    }

    UUID productId = event.getProduct().getId();
    UUID trackedItemId = trackedItemIdByProductId.get(productId);
    if (trackedItemId == null) {
      // User may have untracked after the drop; still skip rather than NPE.
      return null;
    }

    BigDecimal oldPrice = toBigDecimal(payload.get(OutboxPayloadKeys.OLD_PRICE));
    BigDecimal newPrice = toBigDecimal(payload.get(OutboxPayloadKeys.NEW_PRICE));
    if (oldPrice == null || newPrice == null) {
      log.warn("Skipping outbox event {} — missing price keys", event.getId());
      return null;
    }

    return new PriceDropEntry(
        trackedItemId,
        event.getProduct().getName(),
        oldPrice,
        newPrice,
        event.getCreatedAt());
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    try {
      return new BigDecimal(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
