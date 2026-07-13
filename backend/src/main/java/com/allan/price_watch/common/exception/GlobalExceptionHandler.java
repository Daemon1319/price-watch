package com.allan.price_watch.common.exception;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/** Maps exceptions to RFC 9457 ProblemDetail responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final boolean exposeErrorDetails;

  public GlobalExceptionHandler(
      @Value("${app.errors.expose-details:false}") boolean exposeErrorDetails) {
    this.exposeErrorDetails = exposeErrorDetails;
  }

  /** Handles intentional app/business exceptions. */
  @ExceptionHandler(ApplicationException.class)
  public ProblemDetail handleApplicationException(ApplicationException ex, WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    problem.setTitle(ex.getTitle());
    problem.setInstance(URI.create(requestPath(request)));
    problem.setProperty("timestamp", Instant.now());
    return problem;
  }

  /** Handles @Valid request-body validation failures with field errors. */
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

  /** Handles missing or malformed JSON request bodies. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex, WebRequest request) {
    log.warn("Unreadable request body on {}: {}", requestPath(request), ex.getMessage());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "Request body is missing or is not valid JSON. "
            + "In Postman: Body → raw → JSON, and send {\"email\":\"...\",\"password\":\"...\"}.");
    problem.setTitle("Bad Request");
    problem.setInstance(URI.create(requestPath(request)));
    problem.setProperty("timestamp", Instant.now());
    return problem;
  }

  /** Handles unique-constraint and similar DB integrity conflicts. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
    log.warn("Data integrity violation on {}: {}", requestPath(request), ex.getMostSpecificCause().getMessage());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Request conflicts with existing data.");
    problem.setTitle("Conflict");
    problem.setInstance(URI.create(requestPath(request)));
    problem.setProperty("timestamp", Instant.now());
    return problem;
  }

  /** Catch-all for unexpected errors (generic client message). */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpectedException(Exception ex, WebRequest request) {
    log.error("Unhandled exception on {}", requestPath(request), ex);

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    problem.setTitle("Internal Server Error");
    problem.setInstance(URI.create(requestPath(request)));
    problem.setProperty("timestamp", Instant.now());
    if (exposeErrorDetails) {
      problem.setProperty("exception", ex.getClass().getSimpleName());
      problem.setProperty("message", ex.getMessage());
    }
    return problem;
  }

  private String requestPath(WebRequest request) {
    return request.getDescription(false).replace("uri=", "");
  }
}
