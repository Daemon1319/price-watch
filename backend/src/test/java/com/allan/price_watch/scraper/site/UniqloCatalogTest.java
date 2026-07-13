package com.allan.price_watch.scraper.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class UniqloCatalogTest {

  private UniqloCatalog catalog;

  @BeforeEach
  void setUp() {
    catalog = UniqloCatalog.fromClasspath(new JsonMapper(), UniqloCatalog.CLASSPATH_LOCATION);
  }

  @Test
  void loadsColorsFromClasspathResource() {
    assertEquals("Black", catalog.colorDisplayName("COL09").orElseThrow());
    assertEquals("White", catalog.colorDisplayName("00").orElseThrow());
    assertEquals("Navy", catalog.colorDisplayName("69").orElseThrow());
    assertEquals("Light Green", catalog.colorDisplayName("50").orElseThrow());
    assertEquals("Light Blue", catalog.colorDisplayName("60").orElseThrow());
    assertTrue(catalog.colorDisplayName("99").isEmpty());
    assertTrue(catalog.colors().size() >= 40);
  }

  @Test
  void loadsSizesFromClasspathResource() {
    assertEquals("M", catalog.sizeDisplayName("SMA004").orElseThrow());
    assertEquals("XS", catalog.sizeDisplayName("sma002").orElseThrow());
    assertEquals("3XL", catalog.sizeDisplayName("SMA008").orElseThrow());
    assertTrue(catalog.sizeDisplayName("INS029").isEmpty());
  }

  @Test
  void normalizesBareDigitsAndColPrefix() {
    assertEquals("COL09", catalog.normalizeColorCode("09"));
    assertEquals("COL09", catalog.normalizeColorCode("9"));
    assertEquals("COL09", catalog.normalizeColorCode("col09"));
    assertEquals("COL09", catalog.normalizeColorCode("COL09"));
    assertEquals("COL00", catalog.normalizeColorCode("0"));
    assertEquals("COL69", catalog.normalizeColorCode("69"));
  }

  @Test
  void normalizeVariantCodeDistinguishesColorAndSize() {
    assertEquals("COL09", catalog.normalizeVariantCode("09"));
    assertEquals("SMA004", catalog.normalizeVariantCode("sma004"));
    assertEquals("INS029", catalog.normalizeVariantCode("ins029"));
  }
}
