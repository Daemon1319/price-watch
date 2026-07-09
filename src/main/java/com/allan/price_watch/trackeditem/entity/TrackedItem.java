package com.allan.price_watch.trackeditem.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.allan.price_watch.auth.entity.User;
import com.allan.price_watch.product.entity.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One user's subscription to a {@link Product} — the per-user preferences
 * (price threshold, restock-only flag, active/paused) that don't belong on
 * the shared {@code Product} row itself. This is the resource the
 * {@code /tracked-items} endpoints operate on (see the REST endpoint
 * reference doc).
 *
 * <p>The DB-level uniqueness constraint on {@code (user_id, product_id)}
 * (see {@code V1__init_schema.sql}) is what actually prevents duplicate
 * subscriptions — {@code TrackedItemService} should still pre-check via
 * {@code TrackedItemRepository} for a clean {@code 409} instead of letting
 * the constraint violation surface as a raw DB exception.
 */
@Entity
@Table(name = "tracked_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackedItem {

  @Id
  @Generated(event = EventType.INSERT)
  @Column(name = "id", insertable = false, updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false, updatable = false)
  private Product product;

  @Column(name = "price_threshold", precision = 10, scale = 2)
  private BigDecimal priceThreshold;

  @Column(name = "notify_on_restock_only", nullable = false)
  private boolean notifyOnRestockOnly;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TrackedItemStatus status;

  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
  private Instant createdAt;

  @Generated(event = {EventType.INSERT, EventType.UPDATE})
  @Column(name = "updated_at", insertable = false, updatable = false, nullable = false)
  private Instant updatedAt;
}