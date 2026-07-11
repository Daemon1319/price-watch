package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for exceptions the app throws on purpose — a rejected request,
 * a business rule violation — as opposed to unexpected/infrastructure
 * failures (DB connection loss, a bug) that should surface as a generic
 * {@code 500}. {@code GlobalExceptionHandler} has a single
 * {@code @ExceptionHandler(ApplicationException.class)} method that turns
 * any subclass of this into an RFC 9457 {@code ProblemDetail} using the
 * status/title it carries, so adding a new exception type never requires
 * touching the handler.
 */
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