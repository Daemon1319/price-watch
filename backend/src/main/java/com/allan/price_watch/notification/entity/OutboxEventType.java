package com.allan.price_watch.notification.entity;

/** Kind of product change stored in an outbox event. */
public enum OutboxEventType {
  PRICE_DROP,
  PRICE_INCREASE,
  RESTOCK,
  OUT_OF_STOCK
}
