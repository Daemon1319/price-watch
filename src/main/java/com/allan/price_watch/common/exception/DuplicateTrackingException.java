package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateTrackingException extends ApplicationException {

  public DuplicateTrackingException() {
    super(HttpStatus.CONFLICT, "Already Tracking",
        "You are already tracking this product.");
  }
}