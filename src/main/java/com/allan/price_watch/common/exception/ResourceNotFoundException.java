package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Generic 404 for resources that don't have ownership semantics worth a
 * dedicated exception type (unlike {@code TrackedItemNotFoundException},
 * which specifically means "not found, or not yours").
 */
public class ResourceNotFoundException extends ApplicationException {

  public ResourceNotFoundException(String detail) {
    super(HttpStatus.NOT_FOUND, "Resource Not Found", detail);
  }
}