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

/** User-scoped queries for tracked-item subscriptions. */
public interface TrackedItemRepository extends JpaRepository<TrackedItem, UUID> {

  /** Paged list of a user's tracked items with product loaded. */
  @EntityGraph(attributePaths = "product")
  Page<TrackedItem> findByUserId(UUID userId, Pageable pageable);

  /** All tracked items for a user (dashboard lookups). */
  @EntityGraph(attributePaths = "product")
  List<TrackedItem> findByUserId(UUID userId);

  /** Paged list filtered by status. */
  @EntityGraph(attributePaths = "product")
  Page<TrackedItem> findByUserIdAndStatusIn(
      UUID userId, Collection<TrackedItemStatus> statuses, Pageable pageable);

  /** Ownership-safe fetch by tracked-item id and user id. */
  @EntityGraph(attributePaths = "product")
  Optional<TrackedItem> findByIdAndUserId(UUID id, UUID userId);

  /** True if the user already tracks this product. */
  boolean existsByUserIdAndProductId(UUID userId, UUID productId);

  /** Count of items tracked by the user. */
  long countByUserId(UUID userId);

  /** Count of the user's products past the unhealthy failure threshold. */
  long countByUserIdAndProduct_ConsecutiveFailuresGreaterThanEqual(UUID userId, int threshold);

  /** Distinct healthy products with at least one ACTIVE tracker (scheduler). */
  @Query("""
      select distinct ti.product.id from TrackedItem ti
      where ti.status = com.allan.price_watch.trackeditem.entity.TrackedItemStatus.ACTIVE
        and ti.product.consecutiveFailures < :maxFailures
      """)
  List<UUID> findDistinctActiveHealthyProductIds(@Param("maxFailures") int maxFailures);

  /** Active trackers for a product, with user loaded for email delivery. */
  @Query("""
      select ti from TrackedItem ti
      join fetch ti.user
      where ti.product.id = :productId
        and ti.status = com.allan.price_watch.trackeditem.entity.TrackedItemStatus.ACTIVE
      """)
  List<TrackedItem> findActiveByProductIdWithUser(@Param("productId") UUID productId);

  /** User ids tracking a product (dashboard cache invalidation). */
  @Query("select ti.user.id from TrackedItem ti where ti.product.id = :productId")
  List<UUID> findUserIdsByProductId(@Param("productId") UUID productId);
}
