package com.allan.price_watch.scraper.site;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UniqloScraperTest {

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
}
