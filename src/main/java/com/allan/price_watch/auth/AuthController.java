package com.allan.price_watch.auth;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.allan.price_watch.auth.dto.LoginRequest;
import com.allan.price_watch.auth.dto.LoginResponse;
import com.allan.price_watch.auth.dto.RefreshRequest;
import com.allan.price_watch.auth.dto.RegisterRequest;

import jakarta.validation.Valid;

/**
 * {@code register}/{@code login}/{@code refresh} are public per
 * {@code SecurityConfig}; {@code logout} is not — it needs
 * {@code @AuthenticationPrincipal} to resolve which user is revoking a
 * token, which only works if {@code JwtAuthenticationFilter} already ran
 * and populated the {@code SecurityContext}. {@code @AuthenticationPrincipal
 * UUID userId} works here specifically because the filter sets the
 * principal to a raw {@code UUID} rather than a {@code UserDetails} —
 * Spring Security's resolver just returns whatever type the principal
 * actually is.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.ok(authService.register(request));
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @PostMapping("/refresh")
  public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ResponseEntity.ok(authService.refresh(request));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal UUID userId,
      @Valid @RequestBody RefreshRequest request) {
    authService.logout(userId, request);
    return ResponseEntity.noContent().build();
  }
}