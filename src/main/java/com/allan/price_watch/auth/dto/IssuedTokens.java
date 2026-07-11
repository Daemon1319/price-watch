package com.allan.price_watch.auth.dto;

/**
 * Internal login/refresh result: access JWT for the body, raw refresh for the
 * HttpOnly cookie (never log this value).
 */
public record IssuedTokens(
    String accessToken,
    String rawRefreshToken,
    long expiresIn) {
}
