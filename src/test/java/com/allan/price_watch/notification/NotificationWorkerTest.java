package com.allan.price_watch.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.allan.price_watch.notification.entity.OutboxEventType;
import com.allan.price_watch.trackeditem.entity.TrackedItem;

class NotificationWorkerTest {

  @Test
  void restockOnlyIgnoresPriceDrops() {
    TrackedItem tracker = TrackedItem.builder()
        .notifyOnRestockOnly(true)
        .priceThreshold(null)
        .build();

    assertFalse(NotificationWorker.shouldNotify(
        tracker, OutboxEventType.PRICE_DROP, Map.of("oldPrice", "500", "newPrice", "300")));
    assertTrue(NotificationWorker.shouldNotify(
        tracker, OutboxEventType.RESTOCK, Map.of()));
  }

  @Test
  void priceDropRespectsThresholdAsMinimumDropAmount() {
    TrackedItem tracker = TrackedItem.builder()
        .notifyOnRestockOnly(false)
        .priceThreshold(new BigDecimal("200"))
        .build();

    assertFalse(NotificationWorker.shouldNotify(
        tracker, OutboxEventType.PRICE_DROP, Map.of("oldPrice", "500", "newPrice", "400")));
    assertTrue(NotificationWorker.shouldNotify(
        tracker, OutboxEventType.PRICE_DROP, Map.of("oldPrice", "500", "newPrice", "250")));
  }

  @Test
  void nullThresholdNotifiesOnAnyDrop() {
    TrackedItem tracker = TrackedItem.builder()
        .notifyOnRestockOnly(false)
        .priceThreshold(null)
        .build();

    assertTrue(NotificationWorker.shouldNotify(
        tracker, OutboxEventType.PRICE_DROP, Map.of("oldPrice", "100", "newPrice", "99")));
  }
}
