package com.allan.price_watch.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.allan.price_watch.notification.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  /**
   * {@code findByPublishedAtIsNullOrderByCreatedAtAsc} is the exact query
   * {@code OutboxRelay} polls every 5-10s (plan §5, step 4) — oldest
   * unpublished events first, batch size controlled by the {@code Pageable}
   * the caller passes in (e.g. {@code PageRequest.of(0, 50)}) rather than
   * hardcoded here. Matches the partial index
   * {@code outbox_events_unpublished_idx} from {@code V3__outbox_events.sql},
   * so this stays fast regardless of how many published rows accumulate.
   */
  List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

  /**
   * Backs {@code recentPriceDrops} on GET /dashboard/summary.
   * {@code join fetch oe.product} avoids an N+1 when
   * {@code DashboardService} reads {@code product.getName()} off each
   * result — without it, every entry in the list would trigger its own
   * lazy-load query. The subquery scopes results to products this
   * specific user tracks, same reasoning as
   * {@code TrackedItemRepository}'s unhealthy-count query.
   */
  @Query("""
      select oe from OutboxEvent oe
      join fetch oe.product p
      where oe.eventType = com.allan.price_watch.notification.entity.OutboxEventType.PRICE_DROP
        and oe.createdAt >= :since
        and p.id in (select ti.product.id from TrackedItem ti where ti.user.id = :userId)
      order by oe.createdAt desc
      """)
  List<OutboxEvent> findRecentPriceDropsForUser(
      @Param("userId") UUID userId, @Param("since") Instant since, Pageable pageable);
}