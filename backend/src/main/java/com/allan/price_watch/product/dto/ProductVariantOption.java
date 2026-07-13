package com.allan.price_watch.product.dto;

import java.math.BigDecimal;

import com.allan.price_watch.product.entity.StockStatus;

/** One color+size SKU available on a product page. */
public record ProductVariantOption(
    String colorCode,
    String colorName,
    String sizeCode,
    String sizeName,
    BigDecimal price,
    StockStatus stockStatus,
    String thumbnailUrl) {
}
