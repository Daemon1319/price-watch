package com.allan.price_watch.trackeditem.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code priceThreshold} is a <strong>minimum drop amount</strong> (e.g. notify
 * only if price fell by at least 200), not a target price like "alert when ≤ 500".
 * Null means any drop is eligible (subject to {@code notifyOnRestockOnly}).
 */
public record CreateTrackedItemRequest(
    @NotBlank String url,
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal priceThreshold,
    boolean notifyOnRestockOnly) {
}