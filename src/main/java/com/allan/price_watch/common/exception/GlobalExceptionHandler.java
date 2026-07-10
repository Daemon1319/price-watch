package com.allan.price_watch.common.exception;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Every error response from this API is an RFC 9457 {@code ProblemDetail}
 * ({@code application/problem+json}) — see the REST endpoint reference doc.
 * Three handlers cover everything:
 *
 * <ul>
 *   <li>{@link ApplicationException} — any intentional app-level rejection
 *       (see its Javadoc). New exception types just extend it; this class
 *       never needs a new method for them.</li>
 *   <li>{@link MethodArgumentNotValidException} — {@code @Valid} failures
 *       on request bodies, with per-field messages attached.</li>
 *   <li>{@link Exception} — the catch-all for anything unexpected. The
 *       client only ever sees a generic message here; the real exception
 *       is logged server-side, never serialized into the response.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApplicationException.class)
  public ProblemDetail handleApplicationException(ApplicationException ex, WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    problem.setTitle(ex.getTitle());
    problem.setInstance(URI.create(requestPath(request)));
    problem.setProperty("timestamp", Instant.now());
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "One or more fields failed validation.");
    problem.setTitle("Validation Failed");
    problem.setInstance(URI.create(requestPath(request)));
    problem.setProperty("timestamp", Instant.now());

    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.put(error.getField(), error.getDefaultMessage());
    }
    problem.setProperty("fieldErrors", fieldErrors);

    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpectedException(Exception ex, WebRequest request) {
    log.error("Unhandled exception on {}", requestPath(request), ex);

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    problem.setTitle("Internal Server Error");
    problem.setInstance(URI.create(requestPath(request)));
    problem.setProperty("timestamp", Instant.now());
    return problem;
  }

  private String requestPath(WebRequest request) {
    // WebRequest#getDescription(false) returns "uri=/api/v1/...".
    return request.getDescription(false).replace("uri=", "");
  }
}