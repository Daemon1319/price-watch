package com.allan.price_watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.allan.price_watch.product.UrlNormalizer;
import com.allan.price_watch.scraper.site.UniqloCatalog;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIf("com.allan.price_watch.PriceWatchApplicationTests#dockerAvailable")
class PriceWatchApplicationTests {

  @Autowired
  private UniqloCatalog uniqloCatalog;

  @Autowired
  private UrlNormalizer urlNormalizer;

  @Test
  void contextLoads() {
  }

  /** Catalog resource + bean wiring used by variant tracking must be present. */
  @Test
  void uniqloCatalogLoadsColorAndSizeReferenceData() {
    assertFalse(uniqloCatalog.colors().isEmpty(), "colors map from catalog.json");
    assertFalse(uniqloCatalog.sizes().isEmpty(), "sizes map from catalog.json");
    assertEquals("Black", uniqloCatalog.colorDisplayName("COL09").orElseThrow());
    assertEquals("M", uniqloCatalog.sizeDisplayName("SMA004").orElseThrow());
  }

  /** URL normalizer must expand bare color digits the same way production does. */
  @Test
  void urlNormalizerCanonicalizesUniqloVariantQuery() {
    String normalized = urlNormalizer.normalizeWithVariant(
        "https://www.uniqlo.com/ph/en/products/E482465-000",
        "60",
        "sma003");

    assertEquals(
        "https://www.uniqlo.com/ph/en/products/E482465-000?colorCode=COL60&sizeCode=SMA003",
        normalized);
    assertEquals("COL60", urlNormalizer.colorCode(normalized));
    assertEquals("SMA003", urlNormalizer.sizeCode(normalized));
    assertNotNull(uniqloCatalog.colorDisplayName("COL60").orElse(null));
  }

  /**
   * Context load needs Testcontainers (Postgres/Redis/RabbitMQ). Skip cleanly
   * when Docker isn't running so unit tests still pass on bare machines.
   */
  static boolean dockerAvailable() {
    try {
      Process process = new ProcessBuilder("docker", "info")
          .redirectErrorStream(true)
          .start();
      boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
      return finished && process.exitValue() == 0;
    } catch (Exception e) {
      return false;
    }
  }
}
