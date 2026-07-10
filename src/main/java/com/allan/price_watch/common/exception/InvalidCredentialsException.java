package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApplicationException {

  public InvalidCredentialsException() {
    super(HttpStatus.UNAUTHORIZED, "Invalid Credentials",
        "Email or password is incorrect.");
  }
}