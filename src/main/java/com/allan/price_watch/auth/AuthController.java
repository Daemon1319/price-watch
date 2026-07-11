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

/**
 * {@code register}/{@code login}/{@code refresh} are public per
 * {@code SecurityConfig}; {@code logout} is not — it needs a valid access JWT
 * so only the account owner can revoke their cookie-backed refresh session.
 *
 * <p>Refresh tokens are set as HttpOnly cookies. Access JWTs remain in the
 * JSON body for the SPA to hold in memory and send as {@code Bearer}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final RefreshCookieService refreshCookieService;

  public AuthController(AuthService authService, RefreshCookieService refreshCookieService) {
    this.authService = authService;
    this.refreshCookieService = refreshCookieService;
  }

  @PostMapping("/register")
  public ResponseEntity<LoginResponse> register(
      @Valid @RequestBody RegisterRequest request,
      HttpServletResponse response) {
    return okWithRefreshCookie(authService.register(request), response);
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletResponse response) {
    return okWithRefreshCookie(authService.login(request), response);
  }

  /**
   * Cookie-first refresh. Optional JSON body {@code refreshToken} remains for
   * Postman / non-browser clients.
   */
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

  private ResponseEntity<LoginResponse> okWithRefreshCookie(
      IssuedTokens tokens, HttpServletResponse response) {
    refreshCookieService.setRefreshCookie(response, tokens.rawRefreshToken());
    return ResponseEntity.ok(new LoginResponse(tokens.accessToken(), tokens.expiresIn()));
  }
}
