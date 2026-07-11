package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidUrlException extends ApplicationException {

  public InvalidUrlException() {
    super(HttpStatus.BAD_REQUEST, "Invalid URL",
        "The submitted URL could not be parsed.");
  }
}