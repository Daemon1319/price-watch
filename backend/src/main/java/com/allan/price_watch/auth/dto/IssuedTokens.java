package com.allan.price_watch.auth.dto;

/** Internal token pair from login/register/refresh (raw refresh goes in the cookie). */
public record IssuedTokens(
    String accessToken,
    String rawRefreshToken,
    long expiresIn) {
}
