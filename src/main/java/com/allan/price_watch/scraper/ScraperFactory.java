package com.allan.price_watch.scraper;

import java.net.URI;
import java.util.List;

import org.springframework.stereotype.Component;

import com.allan.price_watch.common.exception.UnsupportedSiteException;

/**
 * Spring injects every {@code Scraper} bean found on the classpath here —
 * currently none, since {@code UniqloScraper}/{@code HAndMScraper} haven't
 * been built yet (that's fine, an empty list is a valid injection target,
 * this class just won't be able to resolve anything until they exist).
 * Adding a new supported site later is purely additive: implement
 * {@code Scraper}, and it shows up here automatically without touching
 * this class.
 */
@Component
public class ScraperFactory {

  private final List<Scraper> scrapers;

  public ScraperFactory(List<Scraper> scrapers) {
    this.scrapers = scrapers;
  }

  public Scraper resolve(URI url) {
    return scrapers.stream()
        .filter(scraper -> scraper.supports(url))
        .findFirst()
        .orElseThrow(UnsupportedSiteException::new);
  }
}