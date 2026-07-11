package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** Base type for expected application errors mapped to ProblemDetail responses. */
public abstract class ApplicationException extends RuntimeException {

  private final HttpStatus status;
  private final String title;

  protected ApplicationException(HttpStatus status, String title, String detail) {
    super(detail);
    this.status = status;
    this.title = title;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getTitle() {
    return title;
  }
}
