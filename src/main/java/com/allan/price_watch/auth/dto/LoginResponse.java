package com.allan.price_watch.auth.dto;

/**
 * Auth response: short-lived access JWT plus opaque refresh token.
 *
 * <p>Refresh is also set as an HttpOnly cookie for same-site SPAs. The body field lets
 * cross-origin clients (e.g. Vercel HTTPS → local HTTP API) keep a session when cookies
 * are not sent on credentialed fetches.
 */
public record LoginResponse(
    String accessToken,
    long expiresIn,
    String refreshToken) {
}
