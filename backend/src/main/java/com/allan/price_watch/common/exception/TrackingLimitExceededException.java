package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** 403 when the user hits the max tracked-items cap. */
public class TrackingLimitExceededException extends ApplicationException {

  public TrackingLimitExceededException(int maxItems) {
    super(HttpStatus.FORBIDDEN, "Tracking Limit Exceeded",
        "You can track at most " + maxItems + " products. Delete or pause unused items first.");
  }
}
