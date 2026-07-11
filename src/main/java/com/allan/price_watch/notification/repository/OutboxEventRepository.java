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
   * Outbox relay poll (plan §5 step 4): oldest unpublished first, batch size
   * via {@code Pageable}. Join-fetch product so relay can read product id
   * without an N+1. Matches partial index {@code outbox_events_unpublished_idx}.
   */
  @Query("""
      select oe from OutboxEvent oe
      join fetch oe.product
      where oe.publishedAt is null
      order by oe.createdAt asc
      """)
  List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

  /** Reload one event with product for per-event outbox publish. */
  @Query("""
      select oe from OutboxEvent oe
      join fetch oe.product
      where oe.id = :id
      """)
  java.util.Optional<OutboxEvent> findByIdWithProduct(@Param("id") UUID id);

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