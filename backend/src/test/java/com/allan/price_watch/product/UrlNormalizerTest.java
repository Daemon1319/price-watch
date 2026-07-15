package com.allan.price_watch.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.allan.price_watch.scraper.site.UniqloCatalog;

import tools.jackson.databind.json.JsonMapper;

class UrlNormalizerTest {

  private UrlNormalizer normalizer;

  @BeforeEach
  void setUp() {
    UniqloCatalog catalog = UniqloCatalog.fromClasspath(new JsonMapper(), UniqloCatalog.CLASSPATH_LOCATION);
    normalizer = new UrlNormalizer(catalog);
  }

  @Test
  void uppercasesVariantCodesAndSortsQuery() {
    String normalized = normalizer.normalize(
        "https://www.uniqlo.com/ph/en/products/E471809-000?sizeCode=sma004&utm_source=x&colorCode=col09");

    assertEquals(
        "https://www.uniqlo.com/ph/en/products/E471809-000?colorCode=COL09&sizeCode=SMA004",
        normalized);
  }

  @Test
  void bodyVariantParamsWinOverUrl() {
    String normalized = normalizer.normalizeWithVariant(
        "https://www.uniqlo.com/ph/en/products/E471809-000?colorCode=COL03&sizeCode=SMA002",
        "COL09",
        "SMA004");

    assertEquals(
        "https://www.uniqlo.com/ph/en/products/E471809-000?colorCode=COL09&sizeCode=SMA004",
        normalized);
  }

  @Test
  void bareColorDigitsBecomeColPrefix() {
    String normalized = normalizer.normalizeWithVariant(
        "https://www.uniqlo.com/ph/en/products/E471809-000",
        "09",
        "SMA004");

    assertEquals(
        "https://www.uniqlo.com/ph/en/products/E471809-000?colorCode=COL09&sizeCode=SMA004",
        normalized);

    String fromQuery = normalizer.normalize(
        "https://www.uniqlo.com/ph/en/products/E471809-000?colorCode=9&sizeCode=sma004");
    assertEquals(
        "https://www.uniqlo.com/ph/en/products/E471809-000?colorCode=COL09&sizeCode=SMA004",
        fromQuery);
  }

  @Test
  void readsVariantCodesFromNormalizedUrl() {
    String url = "https://www.uniqlo.com/ph/en/products/E471809-000?colorCode=COL09&sizeCode=INS029";
    assertEquals("COL09", normalizer.colorCode(url));
    assertEquals("INS029", normalizer.sizeCode(url));
    assertNull(normalizer.colorCode("https://www.uniqlo.com/ph/en/products/E471809-000"));
  }

  @Test
  void mapsNewStorefrontDisplayCodesAndStripsDefaultPriceGroupPath() {
    String raw =
        "https://www.uniqlo.com/ph/en/products/E475367-000/00?colorDisplayCode=18&sizeDisplayCode=005";

    assertEquals(
        "https://www.uniqlo.com/ph/en/products/E475367-000?colorCode=COL18&sizeCode=SMA005",
        normalizer.normalize(raw));
    assertEquals("COL18", normalizer.colorCode(raw));
    assertEquals("SMA005", normalizer.sizeCode(raw));
  }

  @Test
  void mapsLargeSizeDisplayCodeToInchPrefix() {
    String raw =
        "https://www.uniqlo.com/ph/en/products/E487742-000/00?colorDisplayCode=30&sizeDisplayCode=028";
    assertEquals(
        "https://www.uniqlo.com/ph/en/products/E487742-000?colorCode=COL30&sizeCode=INS028",
        normalizer.normalize(raw));
  }

  @Test
  void preservesNonDefaultPriceGroupPath() {
    // Different price groups sell different colors (e.g. /00 Black vs /01 Light Gray).
    String raw =
        "https://www.uniqlo.com/ph/en/products/E478550-000/01?colorDisplayCode=02&sizeDisplayCode=003";

    assertEquals(
        "https://www.uniqlo.com/ph/en/products/E478550-000/01?colorCode=COL02&sizeCode=SMA003",
        normalizer.normalize(raw));
    assertEquals("01", UrlNormalizer.uniqloPriceGroupFromPath(
        "/ph/en/products/E478550-000/01"));
    assertNull(UrlNormalizer.uniqloPriceGroupFromPath("/ph/en/products/E478550-000"));
    assertEquals("00", UrlNormalizer.uniqloPriceGroupFromPath(
        "/ph/en/products/E478550-000/00"));
  }
}
