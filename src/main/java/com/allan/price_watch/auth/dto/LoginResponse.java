package com.allan.price_watch.auth.dto;

/**
 * {@code expiresIn} is in seconds, matching the REST endpoint reference doc
 * — returned alongside the tokens so clients don't have to decode the JWT
 * just to know when to refresh.
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn) {
}