package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** 401 when the refresh token is missing, expired, or already used. */
public class InvalidRefreshTokenException extends ApplicationException {

  public InvalidRefreshTokenException() {
    super(HttpStatus.UNAUTHORIZED, "Invalid Refresh Token",
        "The refresh token is invalid, expired, or has already been used.");
  }
}