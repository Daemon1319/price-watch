package com.allan.price_watch.common.exception;

import org.springframework.http.HttpStatus;

/** 404 when a tracked item is missing or not owned by the user. */
public class TrackedItemNotFoundException extends ApplicationException {

  public TrackedItemNotFoundException() {
    super(HttpStatus.NOT_FOUND, "Tracked Item Not Found",
        "No tracked item was found with that id for the current user.");
  }
}