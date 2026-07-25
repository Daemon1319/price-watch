package com.allan.price_watch.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.allan.price_watch.auth.dto.IssuedTokens;
import com.allan.price_watch.auth.dto.LoginRequest;
import com.allan.price_watch.auth.dto.RegisterRequest;
import com.allan.price_watch.common.exception.GlobalExceptionHandler;
import com.allan.price_watch.common.exception.InvalidRefreshTokenException;
import com.allan.price_watch.security.ClientIpResolver;
import com.allan.price_watch.security.JwtService;
import com.allan.price_watch.security.SecurityConfig;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Slice test for {@link AuthController}: validation, error mapping, cookie behavior.
 * No Docker / database required.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean AuthService authService;
  @MockitoBean RefreshCookieService refreshCookieService;

  // Security infrastructure (not under test here)
  @MockitoBean JwtService jwtService;
  @MockitoBean ClientIpResolver clientIpResolver;
  @MockitoBean ProxyManager<String> rateLimitProxyManager;

  @TestConfiguration
  static class RateLimitTestConfig {
    @Bean("apiRateLimitConfiguration")
    BucketConfiguration apiConfig() {
      return BucketConfiguration.builder()
          .addLimit(Bandwidth.builder().capacity(1000).refillGreedy(1000, Duration.ofMinutes(1)).build())
          .build();
    }

    @Bean("authRateLimitConfiguration")
    BucketConfiguration authConfig() {
      return BucketConfiguration.builder()
          .addLimit(Bandwidth.builder().capacity(1000).refillGreedy(1000, Duration.ofMinutes(1)).build())
          .build();
    }
  }

  // ─── Registration ─────────────────────────────────────────────────────────

  @Test
  void registerReturnsTokensAndSetsCookieOnSuccess() throws Exception {
    when(authService.register(any(RegisterRequest.class)))
        .thenReturn(new IssuedTokens("jwt-access", "raw-refresh", 900));

    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"user@example.com","password":"securePass1"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("jwt-access"))
        .andExpect(jsonPath("$.refreshToken").value("raw-refresh"))
        .andExpect(jsonPath("$.expiresIn").value(900));

    verify(refreshCookieService).setRefreshCookie(any(), org.mockito.ArgumentMatchers.eq("raw-refresh"));
  }

  @Test
  void registerRejectsMissingEmail() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"password":"securePass1"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation Failed"))
        .andExpect(jsonPath("$.fieldErrors.email").exists());
  }

  @Test
  void registerRejectsInvalidEmailFormat() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"not-an-email","password":"securePass1"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.email").exists());
  }

  @Test
  void registerRejectsShortPassword() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"user@example.com","password":"short"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.password").exists());
  }

  @Test
  void registerRejectsMissingBody() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  // ─── Login ────────────────────────────────────────────────────────────────

  @Test
  void loginReturnsTokensOnValidCredentials() throws Exception {
    when(authService.login(any(LoginRequest.class)))
        .thenReturn(new IssuedTokens("jwt-token", "refresh-abc", 900));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"user@example.com","password":"correctPass1"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("jwt-token"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-abc"));
  }

  @Test
  void loginRejectsBlankPassword() throws Exception {
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"user@example.com","password":""}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.password").exists());
  }

  // ─── Refresh ──────────────────────────────────────────────────────────────

  @Test
  void refreshReturnsNewTokensWhenCookiePresent() throws Exception {
    when(refreshCookieService.resolveRawRefreshToken(any(), any()))
        .thenReturn("valid-refresh-token");
    when(authService.refresh("valid-refresh-token"))
        .thenReturn(new IssuedTokens("new-jwt", "new-refresh", 900));

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("new-jwt"));
  }

  @Test
  void refreshReturns401WhenNoTokenAvailable() throws Exception {
    when(refreshCookieService.resolveRawRefreshToken(any(), any()))
        .thenReturn(null);

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshReturns401WhenTokenIsRevoked() throws Exception {
    when(refreshCookieService.resolveRawRefreshToken(any(), any()))
        .thenReturn("revoked-token");
    when(authService.refresh("revoked-token"))
        .thenThrow(new InvalidRefreshTokenException());

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isUnauthorized());
  }
}
