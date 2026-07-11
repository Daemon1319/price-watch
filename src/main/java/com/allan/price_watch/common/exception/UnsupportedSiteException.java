package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/**
 * URL host is not handled by any registered {@code Scraper}.
 */
public class UnsupportedSiteException extends ApplicationException {

  public UnsupportedSiteException() {
    super(HttpStatus.BAD_REQUEST, "Unsupported Site",
        "This URL is not from a site PriceWatch supports yet.");
  }
}
