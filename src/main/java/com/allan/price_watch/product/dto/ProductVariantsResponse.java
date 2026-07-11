package com.allan.price_watch.product.dto;

import java.util.List;

/** Catalog of selectable variants for a product URL (before tracking). */
public record ProductVariantsResponse(
    String productName,
    String productId,
    String baseUrl,
    List<ColorOption> colors,
    List<SizeOption> sizes,
    List<ProductVariantOption> variants) {

  public record ColorOption(String code, String name, String displayCode) {
  }

  public record SizeOption(String code, String name, String displayCode) {
  }
}
