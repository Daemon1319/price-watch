package com.allan.price_watch.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the HttpOnly refresh-token cookie. */
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
