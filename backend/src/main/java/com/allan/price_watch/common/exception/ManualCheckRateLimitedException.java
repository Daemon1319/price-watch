package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** 429 when a manual product check is requested too often. */
public class ManualCheckRateLimitedException extends ApplicationException {

  public ManualCheckRateLimitedException(String detail) {
    super(HttpStatus.TOO_MANY_REQUESTS, "Check rate limited", detail);
  }
}
