package com.allan.price_watch.trackeditem.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /** Always fetch product — responses need price/name and open-in-view is off. */
  @EntityGraph(attributePaths = "product")
  Page<TrackedItem> findByUserId(UUID userId, Pageable pageable);

  /**
   * Unpaged overload, used by {@code DashboardService} to build an
   * in-memory {@code productId -> trackedItemId} lookup for this user in
   * one query, rather than N+1 queries while mapping recent price-drop
   * events back to the specific {@code TrackedItem} each one belongs to.
   * Safe to leave unpaged here specifically because it's scoped to one
   * user's own tracked items, which stays small (tens, not thousands) —
   * this is not a pattern to repeat for anything system-wide.
   */
  @EntityGraph(attributePaths = "product")
  List<TrackedItem> findByUserId(UUID userId);

  @EntityGraph(attributePaths = "product")
  Page<TrackedItem> findByUserIdAndStatusIn(
      UUID userId, Collection<TrackedItemStatus> statuses, Pageable pageable);

  @EntityGraph(attributePaths = "product")
  Optional<TrackedItem> findByIdAndUserId(UUID id, UUID userId);

  /** Dedup check backing the {@code 409} response on POST /tracked-items. */
  boolean existsByUserIdAndProductId(UUID userId, UUID productId);

  /** Backs {@code totalTrackedItems} on GET /dashboard/summary. */
  long countByUserId(UUID userId);

  /**
   * Backs {@code unhealthyCount} on GET /dashboard/summary —
   * {@code Product_ConsecutiveFailures} traverses the {@code product}
   * association's {@code consecutiveFailures} field, Spring Data resolves
   * that path automatically from the method name. Deliberately scoped to
   * this user's own tracked products, not a global count across every
   * product in the system — a global count would leak information about
   * (and isn't actionable for) products this user never chose to track.
   */
  long countByUserIdAndProduct_ConsecutiveFailuresGreaterThanEqual(UUID userId, int threshold);

  /**
   * Drives {@code ProductCheckScheduler} — every healthy product with at
   * least one {@code ACTIVE} tracker gets a {@code product.check} job.
   * {@code maxFailures} should be {@link com.allan.price_watch.product.ProductHealth#UNHEALTHY_FAILURE_THRESHOLD}.
   * {@code distinct} so a product tracked by many users is still scraped once.
   */
  @Query("""
      select distinct ti.product.id from TrackedItem ti
      where ti.status = com.allan.price_watch.trackeditem.entity.TrackedItemStatus.ACTIVE
        and ti.product.consecutiveFailures < :maxFailures
      """)
  List<UUID> findDistinctActiveHealthyProductIds(@Param("maxFailures") int maxFailures);

  /**
   * Active subscriptions for a product, with user loaded for notification
   * email. Used by {@code NotificationWorker}.
   */
  @Query("""
      select ti from TrackedItem ti
      join fetch ti.user
      where ti.product.id = :productId
        and ti.status = com.allan.price_watch.trackeditem.entity.TrackedItemStatus.ACTIVE
      """)
  List<TrackedItem> findActiveByProductIdWithUser(@Param("productId") UUID productId);

  /** User ids tracking a product — for dashboard cache invalidation after scrapes. */
  @Query("select ti.user.id from TrackedItem ti where ti.product.id = :productId")
  List<UUID> findUserIdsByProductId(@Param("productId") UUID productId);
}
