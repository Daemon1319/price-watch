package com.allan.price_watch.dashboard;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.allan.price_watch.common.exception.GlobalExceptionHandler;
import com.allan.price_watch.dashboard.dto.DashboardSummaryResponse;
import com.allan.price_watch.security.ClientIpResolver;
import com.allan.price_watch.security.JwtService;
import com.allan.price_watch.security.SecurityConfig;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;

/**
 * Slice test for {@link DashboardController}.
 */
@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DashboardControllerTest {

  private static final String TOKEN = "test-jwt";
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired MockMvc mockMvc;

  @MockitoBean DashboardService dashboardService;

  // Security infrastructure
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

  @BeforeEach
  void authenticateRequests() {
    when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
    when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
  }

  @Test
  void summaryReturnsCountsAndPriceDrops() throws Exception {
    var summary = new DashboardSummaryResponse(
        3,
        List.of(new DashboardSummaryResponse.PriceDropEntry(
            UUID.randomUUID(), "Airism Tee",
            new BigDecimal("990.00"), new BigDecimal("790.00"), Instant.now())),
        1);

    when(dashboardService.getSummary(USER_ID)).thenReturn(summary);

    mockMvc.perform(get("/api/v1/dashboard/summary")
            .header("Authorization", "Bearer " + TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalTrackedItems").value(3))
        .andExpect(jsonPath("$.unhealthyCount").value(1))
        .andExpect(jsonPath("$.recentPriceDrops[0].productName").value("Airism Tee"))
        .andExpect(jsonPath("$.recentPriceDrops[0].oldPrice").value(990.00))
        .andExpect(jsonPath("$.recentPriceDrops[0].newPrice").value(790.00));
  }

  @Test
  void summaryReturnsEmptyWhenNoItems() throws Exception {
    var summary = new DashboardSummaryResponse(0, List.of(), 0);
    when(dashboardService.getSummary(USER_ID)).thenReturn(summary);

    mockMvc.perform(get("/api/v1/dashboard/summary")
            .header("Authorization", "Bearer " + TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalTrackedItems").value(0))
        .andExpect(jsonPath("$.recentPriceDrops").isEmpty());
  }
}
