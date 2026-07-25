package com.allan.price_watch.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.allan.price_watch.auth.AuthController;
import com.allan.price_watch.auth.AuthService;
import com.allan.price_watch.auth.RefreshCookieService;
import com.allan.price_watch.dashboard.DashboardController;
import com.allan.price_watch.dashboard.DashboardService;
import com.allan.price_watch.product.ProductController;
import com.allan.price_watch.product.ProductService;
import com.allan.price_watch.scraper.ProductCheckService;
import com.allan.price_watch.trackeditem.TrackedItemController;
import com.allan.price_watch.trackeditem.TrackedItemService;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;

import java.time.Duration;

/**
 * Verifies the security filter chain: public endpoints are open,
 * everything else requires a valid Bearer JWT.
 *
 * <p>No Docker needed — this is a web-layer slice test.
 */
@WebMvcTest(controllers = {
    AuthController.class,
    ProductController.class,
    TrackedItemController.class,
    DashboardController.class
})
@Import(SecurityConfig.class)
class SecurityRulesTest {

  @Autowired MockMvc mockMvc;

  // --- Controller dependencies (mocked, not under test here) ---
  @MockitoBean AuthService authService;
  @MockitoBean RefreshCookieService refreshCookieService;
  @MockitoBean ProductService productService;
  @MockitoBean ProductCheckService productCheckService;
  @MockitoBean TrackedItemService trackedItemService;
  @MockitoBean DashboardService dashboardService;

  // --- Security infrastructure ---
  @MockitoBean JwtService jwtService;
  @MockitoBean ClientIpResolver clientIpResolver;
  @MockitoBean ProxyManager<String> rateLimitProxyManager;

  @org.springframework.boot.test.context.TestConfiguration
  static class RateLimitTestConfig {
    @org.springframework.context.annotation.Bean("apiRateLimitConfiguration")
    BucketConfiguration apiConfig() {
      return BucketConfiguration.builder()
          .addLimit(Bandwidth.builder().capacity(1000).refillGreedy(1000, Duration.ofMinutes(1)).build())
          .build();
    }

    @org.springframework.context.annotation.Bean("authRateLimitConfiguration")
    BucketConfiguration authConfig() {
      return BucketConfiguration.builder()
          .addLimit(Bandwidth.builder().capacity(1000).refillGreedy(1000, Duration.ofMinutes(1)).build())
          .build();
    }
  }

  // ─── Public endpoints: must NOT return 401 ───────────────────────────────

  @ParameterizedTest
  @ValueSource(strings = {
      "/api/v1/auth/register",
      "/api/v1/auth/login",
      "/api/v1/auth/refresh"
  })
  void publicEndpointsAreNotBlockedBySecurity(String path) throws Exception {
    // Public endpoints must not be rejected by the security filter chain (403).
    // They may still return 400 (validation) or 401 (business logic, e.g. missing
    // refresh token), but never 403 from Spring Security's authorization layer.
    int status = mockMvc.perform(post(path)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andReturn().getResponse().getStatus();

    org.junit.jupiter.api.Assertions.assertNotEquals(403, status,
        path + " should be public but was blocked by security (403)");
  }

  @Test
  void optionsRequestsArePermitted() throws Exception {
    mockMvc.perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .options("/api/v1/tracked-items"))
        .andExpect(status().isOk());
  }

  // ─── Protected endpoints: must be rejected without a token ─────────────────
  // Note: Spring Security returns 403 (not 401) by default when no custom
  // AuthenticationEntryPoint is configured. Both mean "access denied".

  @ParameterizedTest
  @ValueSource(strings = {
      "/api/v1/tracked-items",
      "/api/v1/dashboard/summary",
      "/api/v1/products/check-all"
  })
  void protectedEndpointsRejectWithoutToken(String path) throws Exception {
    mockMvc.perform(get(path))
        .andExpect(status().isForbidden());
  }

  @Test
  void protectedPostEndpointsRejectWithoutToken() throws Exception {
    mockMvc.perform(post("/api/v1/tracked-items")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"url":"https://www.uniqlo.com/ph/en/products/E482465-000"}
                """))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/api/v1/products/check-all"))
        .andExpect(status().isForbidden());
  }

  // ─── Protected endpoints: work with a valid Bearer token ─────────────────

  @Test
  void protectedEndpointAcceptsValidBearerToken() throws Exception {
    UUID userId = UUID.randomUUID();
    String fakeToken = "valid-jwt-token";

    when(jwtService.isTokenValid(fakeToken)).thenReturn(true);
    when(jwtService.extractUserId(fakeToken)).thenReturn(userId);
    when(trackedItemService.list(
        org.mockito.ArgumentMatchers.eq(userId),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyBoolean(),
        org.mockito.ArgumentMatchers.any()))
        .thenReturn(org.springframework.data.domain.Page.empty());

    mockMvc.perform(get("/api/v1/tracked-items")
            .header("Authorization", "Bearer " + fakeToken))
        .andExpect(status().isOk());
  }

  @Test
  void invalidBearerTokenStillRejects() throws Exception {
    when(jwtService.isTokenValid(anyString())).thenReturn(false);

    mockMvc.perform(get("/api/v1/tracked-items")
            .header("Authorization", "Bearer garbage-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void malformedAuthorizationHeaderRejects() throws Exception {
    // No "Bearer " prefix — filter ignores it, no auth set → rejected.
    mockMvc.perform(get("/api/v1/dashboard/summary")
            .header("Authorization", "Basic dXNlcjpwYXNz"))
        .andExpect(status().isForbidden());
  }
}
