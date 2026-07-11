package com.allan.price_watch.scraper;

import java.net.URI;
import java.util.List;

import org.springframework.stereotype.Component;

import com.allan.price_watch.common.exception.UnsupportedSiteException;

/**
 * Resolves a {@link Scraper} for a product URL. Spring injects every
 * {@code Scraper} bean on the classpath. Adding a storefront is additive:
 * implement {@code Scraper}, no change to this class.
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
