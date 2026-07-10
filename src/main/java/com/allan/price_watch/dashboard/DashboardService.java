package com.allan.price_watch.dashboard;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.allan.price_watch.dashboard.dto.DashboardSummaryResponse;
import com.allan.price_watch.dashboard.dto.DashboardSummaryResponse.PriceDropEntry;
import com.allan.price_watch.notification.entity.OutboxEvent;
import com.allan.price_watch.notification.repository.OutboxEventRepository;
import com.allan.price_watch.trackeditem.entity.TrackedItem;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

/**
 * {@code @Cacheable} here is the entire "read-through Redis cache" from
 * plan §5/§12 — Spring's cache abstraction handles the get-or-compute
 * logic itself; there's no manual {@code RedisTemplate} get/set code
 * needed. Cache key is the user's id, so each user gets their own cached
 * summary; TTL (10 min) is configured once in {@code application.yml}
 * rather than here. No explicit eviction anywhere — this is a
 * time-expiry-only cache, meaning the dashboard can be up to 10 minutes
 * stale after a price drop. That's an intentional tradeoff (per plan
 * §12), not a gap: evicting on every price-history write would mean
 * touching Redis from the scrape worker for a value most users aren't
 * actively looking at in that exact moment.
 */
@Service
public class DashboardService {

  private static final int UNHEALTHY_THRESHOLD = 5;
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
        .countByUserIdAndProduct_ConsecutiveFailuresGreaterThanEqual(userId, UNHEALTHY_THRESHOLD);

    List<PriceDropEntry> recentPriceDrops = buildRecentPriceDrops(userId);

    return new DashboardSummaryResponse(totalTrackedItems, recentPriceDrops, unhealthyCount);
  }

  private List<PriceDropEntry> buildRecentPriceDrops(UUID userId) {
    // One query to build productId -> trackedItemId for this user, so
    // mapping each price-drop event below is an in-memory lookup instead
    // of a query per event.
    Map<UUID, UUID> trackedItemIdByProductId = trackedItemRepository
        .findByUserId(userId, Pageable.unpaged()).stream()
        .collect(Collectors.toMap(ti -> ti.getProduct().getId(), TrackedItem::getId));

    List<OutboxEvent> events = outboxEventRepository.findRecentPriceDropsForUser(
        userId, Instant.now().minus(RECENT_WINDOW), PageRequest.of(0, RECENT_PRICE_DROPS_LIMIT));

    return events.stream()
        .map(event -> toPriceDropEntry(event, trackedItemIdByProductId))
        .toList();
  }

  /**
   * {@code payload} is an untyped {@code Map<String, Object>} (see
   * {@code OutboxEvent}'s Javadoc on its native JSON mapping) — this is
   * the one place in the app that has to trust its shape at runtime
   * rather than the compiler. The keys {@code oldPrice}/{@code newPrice}
   * are a convention the eventual {@code ScrapeWorker} must follow when
   * it writes these events; there's no shared constant enforcing it yet
   * since that producer side doesn't exist as code yet either.
   */
  private PriceDropEntry toPriceDropEntry(OutboxEvent event, Map<UUID, UUID> trackedItemIdByProductId) {
    Map<String, Object> payload = event.getPayload();
    UUID productId = event.getProduct().getId();

    return new PriceDropEntry(
        trackedItemIdByProductId.get(productId),
        event.getProduct().getName(),
        new BigDecimal(payload.get("oldPrice").toString()),
        new BigDecimal(payload.get("newPrice").toString()),
        event.getCreatedAt());
  }
}