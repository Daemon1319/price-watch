package com.allan.price_watch.notification.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

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

/** Transactional outbox row for a product change notification. */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

  @Id
  @Generated(event = EventType.INSERT)
  @Column(name = "id", insertable = false, updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false, updatable = false)
  private Product product;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, updatable = false)
  private OutboxEventType eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, updatable = false)
  private Map<String, Object> payload;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
  private Instant createdAt;
}