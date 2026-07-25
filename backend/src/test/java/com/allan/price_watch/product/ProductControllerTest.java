package com.allan.price_watch.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.allan.price_watch.common.exception.GlobalExceptionHandler;
import com.allan.price_watch.product.dto.ManualCheckAllResponse;
import com.allan.price_watch.product.dto.PriceHistoryResponse;
import com.allan.price_watch.product.dto.ProductResponse;
import com.allan.price_watch.product.dto.ProductVariantsResponse;
import com.allan.price_watch.product.dto.ProductVariantOption;
import com.allan.price_watch.product.entity.Site;
import com.allan.price_watch.product.entity.StockStatus;
import com.allan.price_watch.scraper.ProductCheckService;
import com.allan.price_watch.security.ClientIpResolver;
import com.allan.price_watch.security.JwtService;
import com.allan.price_watch.security.SecurityConfig;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;

/**
 * Slice test for {@link ProductController}: routing, param binding, response shape.
 */
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProductControllerTest {

  private static final String TOKEN = "test-jwt";
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired MockMvc mockMvc;

  @MockitoBean ProductService productService;
  @MockitoBean ProductCheckService productCheckService;

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

  // ─── GET /api/v1/products/variants ────────────────────────────────────────

  @Test
  void listVariantsReturnsColorAndSizeOptions() throws Exception {
    var response = new ProductVariantsResponse(
        "Airism Tee", "E482465-000",
        "https://www.uniqlo.com/ph/en/products/E482465-000",
        List.of(new ProductVariantsResponse.ColorOption("COL60", "LIGHT BLUE", "60")),
        List.of(new ProductVariantsResponse.SizeOption("SMA003", "S", "003")),
        List.of(new ProductVariantOption(
            "COL60", "LIGHT BLUE", "SMA003", "S",
            new BigDecimal("990"), StockStatus.IN_STOCK, null)));

    when(productService.listVariants(any())).thenReturn(response);

    mockMvc.perform(get("/api/v1/products/variants")
            .header("Authorization", "Bearer " + TOKEN)
            .param("url", "https://www.uniqlo.com/ph/en/products/E482465-000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productName").value("Airism Tee"))
        .andExpect(jsonPath("$.colors[0].code").value("COL60"))
        .andExpect(jsonPath("$.sizes[0].code").value("SMA003"))
        .andExpect(jsonPath("$.variants[0].colorCode").value("COL60"));
  }

  @Test
  void listVariantsRequiresUrlParam() throws Exception {
    // MissingServletRequestParameterException is caught by GlobalExceptionHandler's
    // catch-all → 500. Ideally should be 400; known gap in exception handler.
    mockMvc.perform(get("/api/v1/products/variants")
            .header("Authorization", "Bearer " + TOKEN))
        .andExpect(status().is5xxServerError());
  }

  // ─── GET /api/v1/products/{id} ────────────────────────────────────────────

  @Test
  void getProductReturnsDetails() throws Exception {
    UUID productId = UUID.randomUUID();
    var response = new ProductResponse(
        productId, "Airism Tee",
        "https://www.uniqlo.com/ph/en/products/E482465-000?colorCode=COL60&sizeCode=SMA003",
        Site.UNIQLO, new BigDecimal("990.00"), StockStatus.IN_STOCK,
        "https://image.uniqlo.com/thumb.jpg",
        "COL60", "LIGHT BLUE", "SMA003", "S",
        Instant.now(), true, null, null);

    when(productService.getById(productId)).thenReturn(response);

    mockMvc.perform(get("/api/v1/products/" + productId)
            .header("Authorization", "Bearer " + TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(productId.toString()))
        .andExpect(jsonPath("$.name").value("Airism Tee"))
        .andExpect(jsonPath("$.lastKnownPrice").value(990.00))
        .andExpect(jsonPath("$.healthy").value(true));
  }

  // ─── GET /api/v1/products/{id}/price-history ──────────────────────────────

  @Test
  void priceHistoryReturnsPagedEntries() throws Exception {
    UUID productId = UUID.randomUUID();
    Page<PriceHistoryResponse> page = new PageImpl<>(List.of(
        new PriceHistoryResponse(new BigDecimal("990.00"), StockStatus.IN_STOCK, Instant.now()),
        new PriceHistoryResponse(new BigDecimal("790.00"), StockStatus.IN_STOCK, Instant.now())));

    when(productService.getPriceHistory(eq(productId), any(), any(), any())).thenReturn(page);

    mockMvc.perform(get("/api/v1/products/" + productId + "/price-history")
            .header("Authorization", "Bearer " + TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].price").value(990.00));
  }

  // ─── POST /api/v1/products/check-all ──────────────────────────────────────

  @Test
  void checkAllReturns202WithQueuedCount() throws Exception {
    when(productCheckService.requestManualCheckAll(USER_ID))
        .thenReturn(new ManualCheckAllResponse(5));

    mockMvc.perform(post("/api/v1/products/check-all")
            .header("Authorization", "Bearer " + TOKEN))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.queued").value(5));
  }

  // ─── POST /api/v1/products/{id}/check ─────────────────────────────────────

  @Test
  void checkSingleProductReturns202() throws Exception {
    UUID productId = UUID.randomUUID();
    var response = new ProductResponse(
        productId, "Airism Tee", "https://www.uniqlo.com/ph/en/products/E482465-000",
        Site.UNIQLO, new BigDecimal("990.00"), StockStatus.IN_STOCK,
        null, "COL60", "LIGHT BLUE", "SMA003", "S",
        Instant.now(), true, null, null);

    when(productCheckService.requestManualCheck(USER_ID, productId)).thenReturn(response);

    mockMvc.perform(post("/api/v1/products/" + productId + "/check")
            .header("Authorization", "Bearer " + TOKEN))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").value(productId.toString()));
  }

  // ─── POST /api/v1/products/{id}/reenable-checks ───────────────────────────

  @Test
  void reenableChecksReturnsProduct() throws Exception {
    UUID productId = UUID.randomUUID();
    var response = new ProductResponse(
        productId, "Airism Tee", "https://www.uniqlo.com/ph/en/products/E482465-000",
        Site.UNIQLO, new BigDecimal("990.00"), StockStatus.IN_STOCK,
        null, "COL60", "LIGHT BLUE", "SMA003", "S",
        Instant.now(), true, null, null);

    when(productService.reenableChecks(USER_ID, productId)).thenReturn(response);

    mockMvc.perform(post("/api/v1/products/" + productId + "/reenable-checks")
            .header("Authorization", "Bearer " + TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.healthy").value(true));
  }
}
