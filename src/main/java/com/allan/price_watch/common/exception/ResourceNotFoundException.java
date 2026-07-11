package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** Generic 404 when a resource id does not exist. */
public class ResourceNotFoundException extends ApplicationException {

  public ResourceNotFoundException(String detail) {
    super(HttpStatus.NOT_FOUND, "Resource Not Found", detail);
  }
}