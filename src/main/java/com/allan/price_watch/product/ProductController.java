package com.allan.price_watch.product;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.allan.price_watch.common.web.PageResponse;
import com.allan.price_watch.product.dto.PriceHistoryResponse;
import com.allan.price_watch.product.dto.ProductResponse;

/**
 * Products are shared, not user-owned (see {@code Product}'s Javadoc), so
 * unlike {@code TrackedItemController} there's no ownership check to make
 * here — {@code SecurityConfig}'s {@code anyRequest().authenticated()} is
 * the entire authorization boundary for these two endpoints. Any
 * authenticated user can view any product's info/price history, since
 * it's shared, deduplicated data rather than something scoped to them.
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
}