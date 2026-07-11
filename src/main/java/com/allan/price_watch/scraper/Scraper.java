package com.allan.price_watch.scraper;

import java.net.URI;

import com.allan.price_watch.product.entity.Site;

/**
 * One implementation per storefront under {@code scraper/site/}.
 * Throw {@code ScrapeFailedException} on hard failure; do not return nulls
 * for required price fields.
 */
public interface Scraper {

  Site getSite();

  boolean supports(URI url);

  ScrapeResult fetch(URI url);
}
