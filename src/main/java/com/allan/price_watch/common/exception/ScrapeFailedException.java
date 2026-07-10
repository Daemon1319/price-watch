package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/**
 * {@code 422}, not {@code 400} or {@code 500} — the request itself was
 * fine (valid, supported URL), but PriceWatch couldn't extract a price/
 * stock value from the page right now. That's a semantically different
 * failure from "you sent bad input" or "we have a bug," per the REST
 * endpoint reference doc.
 */
public class ScrapeFailedException extends ApplicationException {

  public ScrapeFailedException(String detail) {
    super(HttpStatus.UNPROCESSABLE_CONTENT, "Scrape Failed", detail);
  }
}