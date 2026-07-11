package com.allan.price_watch.auth.dto;

/**
 * Optional body for refresh/logout when the client cannot send the HttpOnly
 * cookie (e.g. Postman without a cookie jar). Browser SPA omits this and relies
 * on the cookie alone.
 */
public record RefreshRequest(
    String refreshToken) {
}
