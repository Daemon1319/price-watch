package com.allan.price_watch.product.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.allan.price_watch.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  /**
   * Used by {@code TrackedItemService} on POST /tracked-items to check
   * whether a product row already exists for this URL before creating a
   * new one — the dedup step from plan §5.
   */
  Optional<Product> findByNormalizedUrl(String normalizedUrl);

  /**
   * Backs the {@code unhealthyCount} field on GET /dashboard/summary.
   * {@code threshold} is expected to always be called with {@code 5}, matching
   * the partial index {@code products_consecutive_failures_idx} in
   * {@code V1__init_schema.sql} — keep those two in sync if the threshold
   * ever changes.
   */
  long countByConsecutiveFailuresGreaterThanEqual(int threshold);
}