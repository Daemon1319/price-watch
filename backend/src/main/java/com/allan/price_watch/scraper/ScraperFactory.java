package com.allan.price_watch.scraper;

import java.net.URI;
import java.util.List;

import org.springframework.stereotype.Component;

import com.allan.price_watch.common.exception.UnsupportedSiteException;

/** Picks the Scraper bean that supports a given product URL. */
@Component
public class ScraperFactory {

  private final List<Scraper> scrapers;

  public ScraperFactory(List<Scraper> scrapers) {
    this.scrapers = scrapers;
  }

  /** Returns the matching scraper, or throws if the site is unsupported. */
  public Scraper resolve(URI url) {
    return scrapers.stream()
        .filter(scraper -> scraper.supports(url))
        .findFirst()
        .orElseThrow(UnsupportedSiteException::new);
  }
}
