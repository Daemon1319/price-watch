package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** 401 for wrong email or password. */
public class InvalidCredentialsException extends ApplicationException {

  public InvalidCredentialsException() {
    super(HttpStatus.UNAUTHORIZED, "Invalid Credentials",
        "Email or password is incorrect.");
  }
}