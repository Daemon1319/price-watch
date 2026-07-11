package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** 422 when a valid URL could not be scraped for price/stock. */
public class ScrapeFailedException extends ApplicationException {

  public ScrapeFailedException(String detail) {
    super(HttpStatus.UNPROCESSABLE_CONTENT, "Scrape Failed", detail);
  }
}
