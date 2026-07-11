package com.allan.price_watch.trackeditem.dto;

import java.math.BigDecimal;

import com.allan.price_watch.trackeditem.entity.TrackedItemStatus;

import jakarta.validation.constraints.DecimalMin;

/** PATCH body; null fields mean leave unchanged. */
public record UpdateTrackedItemRequest(
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal priceThreshold,
    Boolean notifyOnRestockOnly,
    TrackedItemStatus status) {
}
