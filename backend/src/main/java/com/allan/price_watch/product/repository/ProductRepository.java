package com.allan.price_watch.product.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.allan.price_watch.product.entity.Product;

/** Product persistence and URL dedup lookup. */
public interface ProductRepository extends JpaRepository<Product, UUID> {

  /** Finds a product by its canonical URL key. */
  Optional<Product> findByNormalizedUrl(String normalizedUrl);

  /** Counts products at or above a consecutive-failure threshold. */
  long countByConsecutiveFailuresGreaterThanEqual(int threshold);
}
