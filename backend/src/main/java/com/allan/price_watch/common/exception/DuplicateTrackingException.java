package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** 409 when the user already tracks this product. */
public class DuplicateTrackingException extends ApplicationException {

  public DuplicateTrackingException() {
    super(HttpStatus.CONFLICT, "Already Tracking",
        "You are already tracking this product.");
  }
}