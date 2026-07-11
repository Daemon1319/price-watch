package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApplicationException {

  public InvalidRefreshTokenException() {
    super(HttpStatus.UNAUTHORIZED, "Invalid Refresh Token",
        "The refresh token is invalid, expired, or has already been used.");
  }
}