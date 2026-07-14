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

/** Shared product row (one per normalized URL) with latest scrape state. */
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

  /** Uniqlo color code, e.g. COL09. Null for legacy non-variant rows. */
  @Column(name = "color_code", length = 20)
  private String colorCode;

  /** Uniqlo size code, e.g. SMA004 (M) or INS029 (29"). */
  @Column(name = "size_code", length = 20)
  private String sizeCode;

  @Column(name = "color_name", length = 100)
  private String colorName;

  @Column(name = "size_name", length = 100)
  private String sizeName;

  @Column(name = "last_checked_at")
  private Instant lastCheckedAt;

  @Column(name = "consecutive_failures", nullable = false)
  private int consecutiveFailures;

  /** Null after a successful scrape; set when the last check failed. */
  @Enumerated(EnumType.STRING)
  @Column(name = "last_failure_reason", length = 40)
  private ScrapeFailureReason lastFailureReason;

  /** Short human-readable failure detail for the UI (not a full stack trace). */
  @Column(name = "last_failure_detail", length = 500)
  private String lastFailureDetail;

  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
  private Instant createdAt;

  @Generated(event = {EventType.INSERT, EventType.UPDATE})
  @Column(name = "updated_at", insertable = false, updatable = false, nullable = false)
  private Instant updatedAt;
}
