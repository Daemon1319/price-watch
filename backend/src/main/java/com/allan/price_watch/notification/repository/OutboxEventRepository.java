package com.allan.price_watch.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.allan.price_watch.notification.entity.OutboxEvent;

/** Queries for outbox relay and dashboard price-drop history. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  /** Oldest unpublished events for the outbox relay poll. */
  @Query("""
      select oe from OutboxEvent oe
      join fetch oe.product
      where oe.publishedAt is null
      order by oe.createdAt asc
      """)
  List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

  /** Reloads one event with product for publish-and-mark. */
  @Query("""
      select oe from OutboxEvent oe
      join fetch oe.product
      where oe.id = :id
      """)
  java.util.Optional<OutboxEvent> findByIdWithProduct(@Param("id") UUID id);

  /** Recent price drops for products the user tracks. */
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
