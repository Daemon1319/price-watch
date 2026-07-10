package com.allan.price_watch.product.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tracked product page, shared across every user who tracks it — this is
 * the deduplicated "one row per normalized URL" resource described in plan
 * §5. Per-user preferences (threshold, restock-only, active/paused) live on
 * {@code TrackedItem} instead, not here.
 *
 * <p>{@code id}, {@code createdAt}, and {@code updatedAt} are database-
 * generated (see {@code V1__init_schema.sql}), same pattern as {@code User}.
 * {@code consecutiveFailures} is intentionally plain and app-managed — the
 * scrape worker increments/resets it directly, there's no DB-side logic for
 * it beyond the partial index that speeds up querying for unhealthy rows.
 *
 * <p>{@code name} (added in {@code V5__add_product_name.sql}) is nullable —
 * a scrape can successfully extract price/stock while failing to find a
 * clean title. Callers should fall back to displaying {@code normalizedUrl}
 * when it's null, not treat a missing name as an error.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

  @Id
  @Generated(event = EventType.INSERT)
  @Column(name = "id", insertable = false, updatable = false, nullable = false)
  private UUID id;

  @Column(name = "name")
  private String name;

  @Column(name = "normalized_url", nullable = false)
  private String normalizedUrl;

  @Column(name = "original_url", nullable = false)
  private String originalUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "site", nullable = false)
  private Site site;

  @Column(name = "last_known_price", precision = 10, scale = 2)
  private BigDecimal lastKnownPrice;

  @Enumerated(EnumType.STRING)
  @Column(name = "last_known_stock_status", nullable = false)
  private StockStatus lastKnownStockStatus;

  @Column(name = "thumbnail_url")
  private String thumbnailUrl;

  @Column(name = "last_checked_at")
  private Instant lastCheckedAt;

  @Column(name = "consecutive_failures", nullable = false)
  private int consecutiveFailures;

  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
  private Instant createdAt;

  @Generated(event = {EventType.INSERT, EventType.UPDATE})
  @Column(name = "updated_at", insertable = false, updatable = false, nullable = false)
  private Instant updatedAt;
}