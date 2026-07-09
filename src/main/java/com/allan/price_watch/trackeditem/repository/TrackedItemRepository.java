package com.allan.price_watch.trackeditem.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.allan.price_watch.trackeditem.entity.TrackedItem;
import com.allan.price_watch.trackeditem.entity.TrackedItemStatus;

/**
 * Every lookup here is scoped by {@code userId} — a tracked item's UUID
 * alone is never enough to fetch it. This is what stops one user from
 * reading, editing, or deleting another user's subscription just by
 * guessing or reusing an id; it's the actual authorization boundary for
 * this resource, enforced at the query level rather than left to the
 * controller/service to remember.
 */
public interface TrackedItemRepository extends JpaRepository<TrackedItem, UUID> {

  Page<TrackedItem> findByUserId(UUID userId, Pageable pageable);

  Page<TrackedItem> findByUserIdAndStatusIn(
      UUID userId, Collection<TrackedItemStatus> statuses, Pageable pageable);

  Optional<TrackedItem> findByIdAndUserId(UUID id, UUID userId);

  /** Dedup check backing the {@code 409} response on POST /tracked-items. */
  boolean existsByUserIdAndProductId(UUID userId, UUID productId);

  /** Backs {@code totalTrackedItems} on GET /dashboard/summary. */
  long countByUserId(UUID userId);
}