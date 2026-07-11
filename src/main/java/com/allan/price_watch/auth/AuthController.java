package com.allan.price_watch.auth;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.allan.price_watch.auth.dto.IssuedTokens;
import com.allan.price_watch.auth.dto.LoginRequest;
import com.allan.price_watch.auth.dto.LoginResponse;
import com.allan.price_watch.auth.dto.RefreshRequest;
import com.allan.price_watch.auth.dto.RegisterRequest;
import com.allan.price_watch.common.exception.InvalidRefreshTokenException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Auth HTTP endpoints: register, login, refresh, and logout. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final RefreshCookieService refreshCookieService;

  public AuthController(AuthService authService, RefreshCookieService refreshCookieService) {
    this.authService = authService;
    this.refreshCookieService = refreshCookieService;
  }

  /** Registers a user and sets the HttpOnly refresh cookie. */
  @PostMapping("/register")
  public ResponseEntity<LoginResponse> register(
      @Valid @RequestBody RegisterRequest request,
      HttpServletResponse response) {
    return okWithRefreshCookie(authService.register(request), response);
  }

  /** Logs in and returns an access token plus refresh cookie. */
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletResponse response) {
    return okWithRefreshCookie(authService.login(request), response);
  }

  /** Issues a new token pair from the refresh cookie (or optional body). */
  @PostMapping("/refresh")
  public ResponseEntity<LoginResponse> refresh(
      @RequestBody(required = false) RefreshRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    String raw = refreshCookieService.resolveRawRefreshToken(
        request, body != null ? body.refreshToken() : null);
    if (raw == null) {
      throw new InvalidRefreshTokenException();
    }
    return okWithRefreshCookie(authService.refresh(raw), response);
  }

  /** Ends the session: revokes the refresh token and clears the cookie. */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal UUID userId,
      @RequestBody(required = false) RefreshRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    String raw = refreshCookieService.resolveRawRefreshToken(
        request, body != null ? body.refreshToken() : null);
    authService.logout(userId, raw);
    refreshCookieService.clearRefreshCookie(response);
    return ResponseEntity.noContent().build();
  }

  /** Writes the refresh cookie and returns the access token JSON body. */
  private ResponseEntity<LoginResponse> okWithRefreshCookie(
      IssuedTokens tokens, HttpServletResponse response) {
    refreshCookieService.setRefreshCookie(response, tokens.rawRefreshToken());
    return ResponseEntity.ok(new LoginResponse(tokens.accessToken(), tokens.expiresIn()));
  }
}
