package com.allan.price_watch.scraper;

import java.net.URI;

import com.allan.price_watch.product.entity.Site;

/** Site-specific product page scraper (one implementation per storefront). */
public interface Scraper {

  /** Site this scraper handles. */
  Site getSite();

  /** Whether this scraper can handle the given product URL. */
  boolean supports(URI url);

  /** Fetches current name, price, stock, and thumbnail for the URL. */
  ScrapeResult fetch(URI url);
}
