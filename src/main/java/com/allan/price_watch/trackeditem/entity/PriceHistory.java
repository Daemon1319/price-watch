package com.allan.price_watch.trackeditem.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.StockStatus;

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
 * One append-only price/stock observation for a {@link Product}, written by
 * the scrape worker whenever a check detects a change (plan §5, step 3d).
 * Rows are never updated or deleted through the app, only inserted — hence
 * no {@code updatedAt} field at all, and {@code recordedAt} only needs
 * {@code EventType.INSERT}.
 */
@Entity
@Table(name = "price_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceHistory {

  @Id
  @Generated(event = EventType.INSERT)
  @Column(name = "id", insertable = false, updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false, updatable = false)
  private Product product;

  @Column(name = "price", nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Enumerated(EnumType.STRING)
  @Column(name = "stock_status", nullable = false)
  private StockStatus stockStatus;

  @Generated(event = EventType.INSERT)
  @Column(name = "recorded_at", insertable = false, updatable = false, nullable = false)
  private Instant recordedAt;
}