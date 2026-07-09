package com.allan.price_watch.trackeditem.entity;

/**
 * Whether a subscription is actively checked by the scheduler. {@code PAUSED}
 * lets a user stop notifications for a product without losing their price
 * threshold/preferences the way deleting the {@code TrackedItem} would.
 */
public enum TrackedItemStatus {
  ACTIVE,
  PAUSED
}