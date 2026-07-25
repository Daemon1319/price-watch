package com.allan.price_watch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * Full-stack integration test: boots the entire application against
 * Testcontainers (Postgres, Redis, RabbitMQ) and exercises real HTTP
 * flows through the security filter chain, controllers, services, and database.
 *
 * <p>Requires Docker. Skipped cleanly when Docker is unavailable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf("com.allan.price_watch.PriceWatchApplicationTests#dockerAvailable")
class AuthFlowIntegrationTest {

  @Autowired MockMvc mockMvc;

  private static final String EMAIL = "integration-" + System.nanoTime() + "@test.com";
  private static final String PASSWORD = "Str0ngPass!";

  private static String accessToken;
  private static String refreshToken;

  private final JsonMapper jsonMapper = new JsonMapper();

  // ─── Step 1: Register ─────────────────────────────────────────────────────

  @Test
  @Order(1)
  void registerCreatesUserAndReturnsTokens() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","password":"%s"}
                """.formatted(EMAIL, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.expiresIn").isNumber())
        .andReturn();

    var body = jsonMapper.readTree(result.getResponse().getContentAsString());
    accessToken = body.get("accessToken").asString();
    refreshToken = body.get("refreshToken").asString();
  }

  // ─── Step 2: Duplicate register → 409 ─────────────────────────────────────

  @Test
  @Order(2)
  void duplicateRegisterReturnsConflict() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","password":"%s"}
                """.formatted(EMAIL, PASSWORD)))
        .andExpect(status().isConflict());
  }

  // ─── Step 3: Login with correct credentials ───────────────────────────────

  @Test
  @Order(3)
  void loginSucceedsWithCorrectCredentials() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","password":"%s"}
                """.formatted(EMAIL, PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andReturn();

    var body = jsonMapper.readTree(result.getResponse().getContentAsString());
    accessToken = body.get("accessToken").asString();
    refreshToken = body.get("refreshToken").asString();
  }

  // ─── Step 4: Login with wrong password → 401 ──────────────────────────────

  @Test
  @Order(4)
  void loginFailsWithWrongPassword() throws Exception {
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"%s","password":"WrongPass1!"}
                """.formatted(EMAIL)))
        .andExpect(status().isUnauthorized());
  }

  // ─── Step 5: Access protected endpoint with token ─────────────────────────

  @Test
  @Order(5)
  void protectedEndpointWorksWithValidToken() throws Exception {
    mockMvc.perform(get("/api/v1/tracked-items")
            .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  // ─── Step 6: Access protected endpoint without token → 401 ────────────────

  @Test
  @Order(6)
  void protectedEndpointRejectsMissingToken() throws Exception {
    mockMvc.perform(get("/api/v1/tracked-items"))
        .andExpect(status().isForbidden());
  }

  // ─── Step 7: Refresh token rotation ───────────────────────────────────────

  @Test
  @Order(7)
  void refreshRotatesTokens() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"refreshToken":"%s"}
                """.formatted(refreshToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andReturn();

    var body = jsonMapper.readTree(result.getResponse().getContentAsString());
    String newRefresh = body.get("refreshToken").asString();

    // Old refresh token is now revoked — reusing it should fail.
    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"refreshToken":"%s"}
                """.formatted(refreshToken)))
        .andExpect(status().isUnauthorized());

    // New refresh token works.
    accessToken = body.get("accessToken").asString();
    refreshToken = newRefresh;
  }

  // ─── Step 8: Dashboard accessible after auth ──────────────────────────────

  @Test
  @Order(8)
  void dashboardSummaryReturnsForAuthenticatedUser() throws Exception {
    mockMvc.perform(get("/api/v1/dashboard/summary")
            .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalTrackedItems").value(0))
        .andExpect(jsonPath("$.recentPriceDrops").isArray());
  }

  // ─── Step 9: Logout revokes refresh token ─────────────────────────────────

  @Test
  @Order(9)
  void logoutRevokesRefreshToken() throws Exception {
    mockMvc.perform(post("/api/v1/auth/logout")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"refreshToken":"%s"}
                """.formatted(refreshToken)))
        .andExpect(status().isNoContent());

    // Refresh token should now be revoked.
    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"refreshToken":"%s"}
                """.formatted(refreshToken)))
        .andExpect(status().isUnauthorized());
  }
}
