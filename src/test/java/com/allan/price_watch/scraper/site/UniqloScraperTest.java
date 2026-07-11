package com.allan.price_watch.scraper.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class UniqloScraperTest {

  private UniqloScraper scraper;

  @BeforeEach
  void setUp() {
    UniqloCatalog catalog = UniqloCatalog.fromClasspath(new JsonMapper(), UniqloCatalog.CLASSPATH_LOCATION);
    scraper = new UniqloScraper(new JsonMapper(), catalog);
  }

  @Test
  void acceptsRealUniqloHosts() {
    assertTrue(UniqloScraper.isAllowedUniqloHost("www.uniqlo.com"));
    assertTrue(UniqloScraper.isAllowedUniqloHost("uniqlo.com"));
    assertTrue(UniqloScraper.isAllowedUniqloHost("WWW.UNIQLO.COM"));
    assertTrue(UniqloScraper.isAllowedUniqloHost("www.uniqlo.co.jp"));
  }

  @Test
  void rejectsLookalikeAndSpoofedHosts() {
    assertFalse(UniqloScraper.isAllowedUniqloHost("uniqlo.com.evil.com"));
    assertFalse(UniqloScraper.isAllowedUniqloHost("evil-uniqlo.com"));
    assertFalse(UniqloScraper.isAllowedUniqloHost("notuniqlo.com"));
    assertFalse(UniqloScraper.isAllowedUniqloHost("uniqlo.com.attacker.io"));
    assertFalse(UniqloScraper.isAllowedUniqloHost(null));
    assertFalse(UniqloScraper.isAllowedUniqloHost(""));
  }

  @Test
  void colorDigitsFromColCodes() {
    assertEquals("09", scraper.colorDigitsFromCode("COL09"));
    assertEquals("03", scraper.colorDigitsFromCode("col03"));
    assertEquals("09", scraper.colorDigitsFromCode("09"));
    assertEquals("09", scraper.colorDigitsFromCode("9"));
    assertNull(scraper.colorDigitsFromCode(null));
    assertNull(scraper.colorDigitsFromCode("SMA004"));
  }

  @Test
  void queryParamReadsVariantCodes() {
    URI url = URI.create(
        "https://www.uniqlo.com/ph/en/products/E471809-000?colorCode=COL09&sizeCode=SMA004");
    assertEquals("COL09", UniqloScraper.queryParam(url, "colorCode"));
    assertEquals("SMA004", UniqloScraper.queryParam(url, "sizeCode"));
  }
}
