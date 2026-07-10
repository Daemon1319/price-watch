package com.allan.price_watch.trackeditem.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

public record CreateTrackedItemRequest(
    @NotBlank String url,
    BigDecimal priceThreshold,
    boolean notifyOnRestockOnly) {
}