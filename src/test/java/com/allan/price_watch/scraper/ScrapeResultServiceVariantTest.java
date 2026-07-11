package com.allan.price_watch.scraper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.StockStatus;

class ScrapeResultServiceVariantTest {

  @Test
  void applyVariantFieldsCopiesCodesAndNames() {
    Product product = Product.builder().build();
    ScrapeResult result = new ScrapeResult(
        "Sweat Pants",
        new BigDecimal("1990"),
        StockStatus.IN_STOCK,
        "https://image.example/x.jpg",
        "COL09",
        "BLACK",
        "SMA004",
        "M");

    ScrapeResultService.applyVariantFields(product, result);

    assertEquals("COL09", product.getColorCode());
    assertEquals("BLACK", product.getColorName());
    assertEquals("SMA004", product.getSizeCode());
    assertEquals("M", product.getSizeName());
  }

  @Test
  void applyVariantFieldsLeavesExistingWhenScrapeOmits() {
    Product product = Product.builder()
        .colorCode("COL09")
        .colorName("BLACK")
        .sizeCode("SMA004")
        .sizeName("M")
        .build();
    ScrapeResult result = ScrapeResult.of(
        "Name", new BigDecimal("100"), StockStatus.IN_STOCK, null);

    ScrapeResultService.applyVariantFields(product, result);

    assertEquals("COL09", product.getColorCode());
    assertEquals("BLACK", product.getColorName());
    assertEquals("SMA004", product.getSizeCode());
    assertEquals("M", product.getSizeName());
  }
}
