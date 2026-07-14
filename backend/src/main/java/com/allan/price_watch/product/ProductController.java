package com.allan.price_watch.product;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.allan.price_watch.common.web.PageResponse;
import com.allan.price_watch.product.dto.ManualCheckAllResponse;
import com.allan.price_watch.product.dto.PriceHistoryResponse;
import com.allan.price_watch.product.dto.ProductResponse;
import com.allan.price_watch.product.dto.ProductVariantsResponse;
import com.allan.price_watch.scraper.ProductCheckService;

/** Product read APIs, manual recheck, and re-enable after scrape failures. */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

  private final ProductService productService;
  private final ProductCheckService productCheckService;

  public ProductController(ProductService productService, ProductCheckService productCheckService) {
    this.productService = productService;
    this.productCheckService = productCheckService;
  }

  /**
   * Lists color/size options for a Uniqlo product URL (no product row created).
   * Call this before POST /tracked-items so the client can pick a SKU.
   */
  @GetMapping("/variants")
  public ProductVariantsResponse listVariants(@RequestParam String url) {
    return productService.listVariants(url);
  }

  /**
   * Queues scrapes for every ACTIVE product the caller tracks.
   * Cooldown: 1 hour per user (configurable).
   */
  @PostMapping("/check-all")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ManualCheckAllResponse requestCheckAll(@AuthenticationPrincipal UUID userId) {
    return productCheckService.requestManualCheckAll(userId);
  }

  /** Returns product details by id. */
  @GetMapping("/{id}")
  public ProductResponse get(@PathVariable UUID id) {
    return productService.getById(id);
  }

  /** Returns paginated price history for a product. */
  @GetMapping("/{id}/price-history")
  public PageResponse<PriceHistoryResponse> priceHistory(
      @PathVariable UUID id,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @PageableDefault(size = 20) Pageable pageable) {
    return PageResponse.from(productService.getPriceHistory(id, from, to, pageable));
  }

  /**
   * Queues an immediate scrape for a product the caller tracks.
   * Rate limited: 15 minutes per product, 20 checks per user per hour (configurable).
   */
  @PostMapping("/{id}/check")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ProductResponse requestCheck(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID id) {
    return productCheckService.requestManualCheck(userId, id);
  }

  /** Clears failure streak so scheduled checks resume for this product. */
  @PostMapping("/{id}/reenable-checks")
  public ProductResponse reenableChecks(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID id) {
    return productService.reenableChecks(userId, id);
  }
}
