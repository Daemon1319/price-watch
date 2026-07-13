package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** 409 when registering an email that already exists. */
public class EmailAlreadyRegisteredException extends ApplicationException {

  public EmailAlreadyRegisteredException() {
    super(HttpStatus.CONFLICT, "Email Already Registered",
        "An account with this email already exists.");
  }
}