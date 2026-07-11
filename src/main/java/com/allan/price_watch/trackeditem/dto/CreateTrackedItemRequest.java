package com.allan.price_watch.trackeditem.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/** Request to start tracking a product URL and notify preferences. */
public record CreateTrackedItemRequest(
    @NotBlank String url,
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal priceThreshold,
    boolean notifyOnRestockOnly) {
}
