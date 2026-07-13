package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** 400 when tracking Uniqlo without a color + size selection. */
public class VariantRequiredException extends ApplicationException {

  public VariantRequiredException() {
    super(HttpStatus.BAD_REQUEST, "Variant required",
        "Uniqlo tracking requires colorCode and sizeCode "
            + "(e.g. COL09 + SMA004). Call GET /api/v1/products/variants?url=... first.");
  }
}
