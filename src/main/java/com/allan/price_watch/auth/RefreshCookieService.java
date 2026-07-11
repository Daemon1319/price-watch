package com.allan.price_watch.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sets / clears the opaque refresh token as an HttpOnly cookie so browser JS
 * cannot read it (unlike {@code localStorage}). Access JWTs stay in the JSON
 * body and are held in memory on the SPA.
 */
@Service
public class RefreshCookieService {

  private final RefreshCookieProperties props;

  public RefreshCookieService(RefreshCookieProperties props) {
    this.props = props;
  }

  public String cookieName() {
    return props.name();
  }

  public void setRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
    response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(rawRefreshToken, duration()).toString());
  }

  public void clearRefreshCookie(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
  }

  /**
   * Prefer cookie; optional JSON body is still accepted for Postman / non-browser
   * clients that cannot use a cookie jar.
   */
  public String resolveRawRefreshToken(HttpServletRequest request, String bodyToken) {
    String fromCookie = readCookie(request);
    if (fromCookie != null && !fromCookie.isBlank()) {
      return fromCookie;
    }
    if (bodyToken != null && !bodyToken.isBlank()) {
      return bodyToken;
    }
    return null;
  }

  private String readCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (props.name().equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private ResponseCookie buildCookie(String value, Duration maxAge) {
    return ResponseCookie.from(props.name(), value)
        .httpOnly(true)
        .secure(props.secure())
        .path(props.path())
        .maxAge(maxAge)
        .sameSite(props.sameSite())
        .build();
  }

  private Duration duration() {
    return Duration.ofDays(props.maxAgeDays());
  }
}
