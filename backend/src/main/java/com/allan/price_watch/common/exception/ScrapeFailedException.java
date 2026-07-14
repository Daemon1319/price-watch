package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

import com.allan.price_watch.product.entity.ScrapeFailureReason;

/** 422 when a valid URL could not be scraped for price/stock. */
public class ScrapeFailedException extends ApplicationException {

  private final ScrapeFailureReason reason;

  public ScrapeFailedException(String detail) {
    this(ScrapeFailureReason.UNKNOWN, detail);
  }

  public ScrapeFailedException(ScrapeFailureReason reason, String detail) {
    super(HttpStatus.UNPROCESSABLE_CONTENT, "Scrape Failed", detail);
    this.reason = reason != null ? reason : ScrapeFailureReason.UNKNOWN;
  }

  public ScrapeFailureReason getReason() {
    return reason;
  }
}
