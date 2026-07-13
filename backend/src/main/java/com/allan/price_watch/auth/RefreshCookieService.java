package com.allan.price_watch.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Manages the HttpOnly refresh-token cookie for browser clients. */
@Service
public class RefreshCookieService {

  private final RefreshCookieProperties props;

  public RefreshCookieService(RefreshCookieProperties props) {
    this.props = props;
  }

  /** Cookie name configured for the refresh token. */
  public String cookieName() {
    return props.name();
  }

  /** Sets the refresh token as an HttpOnly cookie. */
  public void setRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
    response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(rawRefreshToken, duration()).toString());
  }

  /** Clears the refresh cookie (logout). */
  public void clearRefreshCookie(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
  }

  /**
   * Prefers an explicit body token over the cookie.
   *
   * <p>Cross-origin SPAs (e.g. Vercel → local API) keep the rotated refresh token in
   * localStorage and send it in the body. The browser may still attach an older cookie
   * for {@code localhost}; if we preferred the cookie, every refresh after the first
   * would 401 on a revoked token while the body held a valid one.
   */
  public String resolveRawRefreshToken(HttpServletRequest request, String bodyToken) {
    if (bodyToken != null && !bodyToken.isBlank()) {
      return bodyToken;
    }
    String fromCookie = readCookie(request);
    if (fromCookie != null && !fromCookie.isBlank()) {
      return fromCookie;
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
