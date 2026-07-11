package com.allan.price_watch.product;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.allan.price_watch.common.exception.InvalidUrlException;
import com.allan.price_watch.common.exception.ResourceNotFoundException;
import com.allan.price_watch.product.dto.PriceHistoryResponse;
import com.allan.price_watch.product.dto.ProductResponse;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.repository.ProductRepository;
import com.allan.price_watch.scraper.ScrapeResult;
import com.allan.price_watch.scraper.Scraper;
import com.allan.price_watch.scraper.ScraperFactory;
import com.allan.price_watch.trackeditem.entity.PriceHistory;
import com.allan.price_watch.trackeditem.repository.PriceHistoryRepository;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

@Service
public class ProductService {

  private final ProductRepository productRepository;
  private final PriceHistoryRepository priceHistoryRepository;
  private final TrackedItemRepository trackedItemRepository;
  private final UrlNormalizer urlNormalizer;
  private final ScraperFactory scraperFactory;
  private final TransactionTemplate transactionTemplate;

  public ProductService(
      ProductRepository productRepository,
      PriceHistoryRepository priceHistoryRepository,
      TrackedItemRepository trackedItemRepository,
      UrlNormalizer urlNormalizer,
      ScraperFactory scraperFactory,
      TransactionTemplate transactionTemplate) {
    this.productRepository = productRepository;
    this.priceHistoryRepository = priceHistoryRepository;
    this.trackedItemRepository = trackedItemRepository;
    this.urlNormalizer = urlNormalizer;
    this.scraperFactory = scraperFactory;
    this.transactionTemplate = transactionTemplate;
  }

  /**
   * Dedup + first-fetch. New URLs scrape outside a DB transaction, then persist.
   * Thumbnail is the Uniqlo CDN URL from the scraper (no MinIO / no expiry).
   */
  public Product findOrCreateByUrl(String rawUrl) {
    String normalizedUrl;
    URI uri;
    try {
      normalizedUrl = urlNormalizer.normalize(rawUrl);
      uri = URI.create(normalizedUrl);
    } catch (IllegalArgumentException e) {
      throw new InvalidUrlException();
    }

    return productRepository.findByNormalizedUrl(normalizedUrl)
        .orElseGet(() -> createFromScrape(rawUrl, normalizedUrl, uri));
  }

  public ProductResponse getById(UUID id) {
    return productRepository.findById(id)
        .map(ProductResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("No product found with id " + id));
  }

  public Page<PriceHistoryResponse> getPriceHistory(UUID productId, Instant from, Instant to, Pageable pageable) {
    if (!productRepository.existsById(productId)) {
      throw new ResourceNotFoundException("No product found with id " + productId);
    }

    Page<PriceHistory> page = (from == null || to == null)
        ? priceHistoryRepository.findByProductIdOrderByRecordedAtDesc(productId, pageable)
        : priceHistoryRepository.findByProductIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            productId, from, to, pageable);

    return page.map(PriceHistoryResponse::from);
  }

  @Transactional
  public ProductResponse reenableChecks(UUID userId, UUID productId) {
    if (!trackedItemRepository.existsByUserIdAndProductId(userId, productId)) {
      throw new ResourceNotFoundException("No product found with id " + productId);
    }

    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new ResourceNotFoundException("No product found with id " + productId));

    product.setConsecutiveFailures(0);
    return ProductResponse.from(product);
  }

  private Product createFromScrape(String originalUrl, String normalizedUrl, URI uri) {
    Scraper scraper = scraperFactory.resolve(uri);
    ScrapeResult result = scraper.fetch(uri);

    try {
      return transactionTemplate.execute(
          status -> persistNewProduct(originalUrl, normalizedUrl, scraper, result));
    } catch (DataIntegrityViolationException e) {
      return productRepository.findByNormalizedUrl(normalizedUrl)
          .orElseThrow(() -> e);
    }
  }

  private Product persistNewProduct(
      String originalUrl, String normalizedUrl, Scraper scraper, ScrapeResult result) {
    return productRepository.findByNormalizedUrl(normalizedUrl)
        .orElseGet(() -> {
          Product product = Product.builder()
              .name(result.name())
              .normalizedUrl(normalizedUrl)
              .originalUrl(originalUrl)
              .site(scraper.getSite())
              .lastKnownPrice(result.price())
              .lastKnownStockStatus(result.stockStatus())
              .thumbnailUrl(result.thumbnailUrl())
              .lastCheckedAt(Instant.now())
              .consecutiveFailures(0)
              .build();

          product = productRepository.save(product);

          if (result.price() != null) {
            priceHistoryRepository.save(PriceHistory.builder()
                .product(product)
                .price(result.price())
                .stockStatus(result.stockStatus())
                .build());
          }

          return product;
        });
  }
}
