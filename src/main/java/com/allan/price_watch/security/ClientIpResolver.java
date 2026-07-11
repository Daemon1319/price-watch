package com.allan.price_watch.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client IP for rate limiting. {@code X-Forwarded-For} is only
 * honoured when {@code app.rate-limit.trust-forwarded-headers=true} — that
 * must only be enabled behind a reverse proxy that <em>overwrites</em>
 * client-supplied XFF. Blind trust lets an attacker mint a fresh rate-limit
 * bucket per spoofed IP.
 */
@Component
public class ClientIpResolver {

  private final boolean trustForwardedHeaders;

  public ClientIpResolver(
      @Value("${app.rate-limit.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
    this.trustForwardedHeaders = trustForwardedHeaders;
  }

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
