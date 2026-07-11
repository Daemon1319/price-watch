package com.allan.price_watch.notification.dto;

import java.util.Map;
import java.util.UUID;

import com.allan.price_watch.notification.entity.OutboxEventType;

/** Queue message for notification delivery. */
public record NotificationMessage(
    UUID outboxEventId,
    UUID productId,
    OutboxEventType eventType,
    Map<String, Object> payload) {
}
