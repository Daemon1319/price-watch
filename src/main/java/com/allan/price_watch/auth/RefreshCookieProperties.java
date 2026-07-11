package com.allan.price_watch.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HttpOnly refresh-token cookie. Cross-origin SPA (e.g. Next on :3000, API on
 * :8080) needs {@code same-site=none} and {@code secure=true} so the browser
 * attaches the cookie on credentialed {@code fetch} calls.
 */
@ConfigurationProperties("app.auth.refresh-cookie")
public record RefreshCookieProperties(
    String name,
    String path,
    long maxAgeDays,
    String sameSite,
    boolean secure) {

  public RefreshCookieProperties {
    if (name == null || name.isBlank()) {
      name = "pw_refresh";
    }
    if (path == null || path.isBlank()) {
      path = "/api/v1/auth";
    }
    if (maxAgeDays <= 0) {
      maxAgeDays = 7;
    }
    if (sameSite == null || sameSite.isBlank()) {
      sameSite = "None";
    }
  }
}
