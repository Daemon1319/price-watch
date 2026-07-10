package com.allan.price_watch.trackeditem.dto;

import java.math.BigDecimal;

import com.allan.price_watch.trackeditem.entity.TrackedItemStatus;

/**
 * PATCH semantics: any field left {@code null} means "don't change it."
 * There's deliberately no way to explicitly clear {@code priceThreshold}
 * back to "no threshold" with this shape — out of scope for v1 (plan §14),
 * revisit if that need comes up.
 */
public record UpdateTrackedItemRequest(
    BigDecimal priceThreshold,
    Boolean notifyOnRestockOnly,
    TrackedItemStatus status) {
}