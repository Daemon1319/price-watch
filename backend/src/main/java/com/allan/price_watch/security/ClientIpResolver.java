package com.allan.price_watch.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves client IP for rate limiting, optionally trusting X-Forwarded-For. */
@Component
public class ClientIpResolver {

  private final boolean trustForwardedHeaders;

  public ClientIpResolver(
      @Value("${app.rate-limit.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
    this.trustForwardedHeaders = trustForwardedHeaders;
  }

  /** Returns the client IP, using X-Forwarded-For only when configured. */
  public String resolve(HttpServletRequest request) {
    if (trustForwardedHeaders) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].trim();
      }
    }
    return request.getRemoteAddr();
  }

  public boolean trustsForwardedHeaders() {
    return trustForwardedHeaders;
  }
}
