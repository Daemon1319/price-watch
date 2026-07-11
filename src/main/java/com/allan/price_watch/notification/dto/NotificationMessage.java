package com.allan.price_watch.notification.dto;

import java.util.Map;
import java.util.UUID;

import com.allan.price_watch.notification.entity.OutboxEventType;

/**
 * Payload published by {@code OutboxRelay} onto {@code notification.send}.
 * Carries enough context for the worker to filter and render email without
 * re-reading the outbox row (the row may already be marked published).
 */
public record NotificationMessage(
    UUID outboxEventId,
    UUID productId,
    OutboxEventType eventType,
    Map<String, Object> payload) {
}
