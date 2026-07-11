package com.allan.price_watch.trackeditem.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to start tracking a product URL and notify preferences.
 *
 * <p>For Uniqlo, {@code colorCode} + {@code sizeCode} are required (body or URL query).
 * Examples: {@code COL09} (black), {@code SMA004} (M), {@code INS029} (29&quot;).
 */
public record CreateTrackedItemRequest(
    @NotBlank String url,
    String colorCode,
    String sizeCode,
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal priceThreshold,
    boolean notifyOnRestockOnly) {
}
