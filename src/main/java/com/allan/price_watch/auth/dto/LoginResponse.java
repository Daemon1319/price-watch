package com.allan.price_watch.auth.dto;

/**
 * Access JWT only in the response body. The opaque refresh token is delivered
 * via an HttpOnly cookie (see {@code RefreshCookieService}) so browser JS
 * cannot read it. {@code expiresIn} is the access-token TTL in seconds.
 */
public record LoginResponse(
    String accessToken,
    long expiresIn) {
}
