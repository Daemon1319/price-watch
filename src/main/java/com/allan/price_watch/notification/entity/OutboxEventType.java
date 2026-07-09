package com.allan.price_watch.notification.entity;

/**
 * The kind of change an {@link OutboxEvent} is reporting. Values match the
 * CHECK constraint on {@code outbox_events.event_type} in
 * {@code V3__outbox_events.sql} exactly — keep the two in sync if this ever
 * grows.
 */
public enum OutboxEventType {
  PRICE_DROP,
  PRICE_INCREASE,
  RESTOCK,
  OUT_OF_STOCK
}