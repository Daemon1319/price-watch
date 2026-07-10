package com.allan.price_watch.scraper;

import java.net.URI;

import com.allan.price_watch.product.entity.Site;

/**
 * One implementation per supported storefront (see {@code scraper/site/}).
 * {@code fetch} is expected to throw {@code ScrapeFailedException} rather
 * than return a partial/null result on parse failure — {@code ProductService}
 * and the eventual {@code ScrapeWorker} both rely on that to distinguish
 * "got a real result" from "something went wrong," rather than checking
 * for null fields after the fact.
 */
public interface Scraper {

  Site getSite();

  boolean supports(URI url);

  ScrapeResult fetch(URI url);
}