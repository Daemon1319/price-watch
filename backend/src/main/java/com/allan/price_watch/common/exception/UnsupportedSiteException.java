package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** Requested URL is not from a supported storefront. */
public class UnsupportedSiteException extends ApplicationException {

  public UnsupportedSiteException() {
    super(HttpStatus.BAD_REQUEST, "Unsupported Site",
        "This URL is not from a site PriceWatch supports yet.");
  }
}
