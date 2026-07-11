package com.allan.price_watch.trackeditem.dto;

import java.math.BigDecimal;

import com.allan.price_watch.trackeditem.entity.TrackedItemStatus;

import jakarta.validation.constraints.DecimalMin;

/**
 * PATCH semantics: any field left {@code null} means "don't change it."
 * There's deliberately no way to explicitly clear {@code priceThreshold}
 * back to "no threshold" with this shape — out of scope for v1 (plan §14),
 * revisit if that need comes up.
 *
 * <p>{@code priceThreshold} is a <strong>minimum drop amount</strong> (same
 * currency as the product price), not a target price. When non-null it must
 * be ≥ 0, matching {@link CreateTrackedItemRequest}.
 */
public record UpdateTrackedItemRequest(
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal priceThreshold,
    Boolean notifyOnRestockOnly,
    TrackedItemStatus status) {
}