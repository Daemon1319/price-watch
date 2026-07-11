package com.allan.price_watch.auth.dto;

/** Auth response body: access JWT and its TTL in seconds. */
public record LoginResponse(
    String accessToken,
    long expiresIn) {
}
