package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** 400 when the requested color/size combo is not sold for the product. */
public class InvalidVariantException extends ApplicationException {

  public InvalidVariantException(String colorCode, String sizeCode) {
    super(HttpStatus.BAD_REQUEST, "Invalid variant",
        "No Uniqlo SKU for colorCode=" + colorCode + " sizeCode=" + sizeCode + ".");
  }
}
