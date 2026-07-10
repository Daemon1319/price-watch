package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Same {@code 400} status as {@link InvalidUrlException} — from the
 * client's perspective "malformed URL" and "URL for a site we don't
 * support yet" are both just "this URL isn't going to work," per the REST
 * endpoint reference doc. Kept as a distinct exception type rather than
 * reusing {@code InvalidUrlException} so the {@code detail} message can be
 * specific.
 */
public class UnsupportedSiteException extends ApplicationException {

  public UnsupportedSiteException() {
    super(HttpStatus.BAD_REQUEST, "Unsupported Site",
        "This URL isn't from a site PriceWatch supports yet.");
  }
}