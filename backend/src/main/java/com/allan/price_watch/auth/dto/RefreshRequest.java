package com.allan.price_watch.auth.dto;

/** Optional refresh token body for non-browser clients without cookies. */
public record RefreshRequest(
    String refreshToken) {
}
