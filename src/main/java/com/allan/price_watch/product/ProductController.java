package com.allan.price_watch.product;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.allan.price_watch.common.web.PageResponse;
import com.allan.price_watch.product.dto.PriceHistoryResponse;
import com.allan.price_watch.product.dto.ProductResponse;

/**
 * Products are shared across users. Read endpoints are available to any
 * authenticated caller. {@code reenable-checks} is limited to users who
 * track that product (enforced in {@link ProductService}).
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @GetMapping("/{id}")
  public ProductResponse get(@PathVariable UUID id) {
    return productService.getById(id);
  }

  @GetMapping("/{id}/price-history")
  public PageResponse<PriceHistoryResponse> priceHistory(
      @PathVariable UUID id,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @PageableDefault(size = 20) Pageable pageable) {
    return PageResponse.from(productService.getPriceHistory(id, from, to, pageable));
  }

  /**
   * After 5 scrape failures a product is skipped by the scheduler. Call this
   * once you've fixed the scraper (or the site is healthy again) so checks resume.
   */
  @PostMapping("/{id}/reenable-checks")
  public ProductResponse reenableChecks(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID id) {
    return productService.reenableChecks(userId, id);
  }
}