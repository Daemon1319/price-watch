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
import com.allan.price_watch.common.exception.VariantRequiredException;
import com.allan.price_watch.product.dto.PriceHistoryResponse;
import com.allan.price_watch.product.dto.ProductResponse;
import com.allan.price_watch.product.dto.ProductVariantsResponse;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.Site;
import com.allan.price_watch.product.repository.ProductRepository;
import com.allan.price_watch.scraper.ScrapeResult;
import com.allan.price_watch.scraper.Scraper;
import com.allan.price_watch.scraper.ScraperFactory;
import com.allan.price_watch.scraper.site.UniqloCatalog;
import com.allan.price_watch.scraper.site.UniqloScraper;
import com.allan.price_watch.trackeditem.entity.PriceHistory;
import com.allan.price_watch.trackeditem.repository.PriceHistoryRepository;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

/** Looks up products by URL, creates them from a first scrape, and serves reads. */
@Service
public class ProductService {

  private final ProductRepository productRepository;
  private final PriceHistoryRepository priceHistoryRepository;
  private final TrackedItemRepository trackedItemRepository;
  private final UrlNormalizer urlNormalizer;
  private final UniqloCatalog uniqloCatalog;
  private final ScraperFactory scraperFactory;
  private final TransactionTemplate transactionTemplate;

  public ProductService(
      ProductRepository productRepository,
      PriceHistoryRepository priceHistoryRepository,
      TrackedItemRepository trackedItemRepository,
      UrlNormalizer urlNormalizer,
      UniqloCatalog uniqloCatalog,
      ScraperFactory scraperFactory,
      TransactionTemplate transactionTemplate) {
    this.productRepository = productRepository;
    this.priceHistoryRepository = priceHistoryRepository;
    this.trackedItemRepository = trackedItemRepository;
    this.urlNormalizer = urlNormalizer;
    this.uniqloCatalog = uniqloCatalog;
    this.scraperFactory = scraperFactory;
    this.transactionTemplate = transactionTemplate;
  }

  /**
   * Returns an existing product for the URL, or scrapes and creates one.
   * Uniqlo URLs must include colorCode + sizeCode (variant-level tracking).
   */
  public Product findOrCreateByUrl(String rawUrl) {
    return findOrCreateByUrl(rawUrl, null, null);
  }

  /**
   * Like {@link #findOrCreateByUrl(String)} but merges optional body color/size into the URL.
   */
  public Product findOrCreateByUrl(String rawUrl, String colorCode, String sizeCode) {
    String normalizedUrl;
    URI uri;
    try {
      normalizedUrl = urlNormalizer.normalizeWithVariant(rawUrl, colorCode, sizeCode);
      uri = URI.create(normalizedUrl);
    } catch (IllegalArgumentException e) {
      throw new InvalidUrlException();
    }

    Scraper scraper = scraperFactory.resolve(uri);
    requireVariantIfNeeded(scraper.getSite(), normalizedUrl);

    return productRepository.findByNormalizedUrl(normalizedUrl)
        .map(existing -> backfillVariantFieldsIfMissing(existing, normalizedUrl))
        .orElseGet(() -> createFromScrape(rawUrl, normalizedUrl, uri, scraper));
  }

  /** Lists color/size options for a product URL without creating a product row. */
  public ProductVariantsResponse listVariants(String rawUrl) {
    URI uri;
    try {
      // Strip tracking junk but do not require variant params for discovery.
      String normalized = urlNormalizer.normalize(rawUrl);
      uri = URI.create(normalized);
    } catch (IllegalArgumentException e) {
      throw new InvalidUrlException();
    }

    Scraper scraper = scraperFactory.resolve(uri);
    if (!(scraper instanceof UniqloScraper uniqloScraper)) {
      throw new ResourceNotFoundException("Variant listing is only supported for Uniqlo URLs.");
    }
    return uniqloScraper.listVariants(uri);
  }

  /** Uniqlo tracking is always size+color specific. */
  private void requireVariantIfNeeded(Site site, String normalizedUrl) {
    if (site != Site.UNIQLO) {
      return;
    }
    String color = urlNormalizer.colorCode(normalizedUrl);
    String size = urlNormalizer.sizeCode(normalizedUrl);
    if (color == null || size == null) {
      throw new VariantRequiredException();
    }
  }

  /** Loads a product by id for API responses. */
  public ProductResponse getById(UUID id) {
    return productRepository.findById(id)
        .map(ProductResponse::from)
        .orElseThrow(() -> new ResourceNotFoundException("No product found with id " + id));
  }

  /** Paged price history for a product, optionally filtered by time range. */
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

  /** Resets consecutive scrape failures so the scheduler resumes checks. */
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

  /** Scrapes a new URL outside a TX, then persists product + first price point. */
  private Product createFromScrape(
      String originalUrl, String normalizedUrl, URI uri, Scraper scraper) {
    ScrapeResult result = scraper.fetch(uri);

    try {
      return transactionTemplate.execute(
          status -> persistNewProduct(originalUrl, normalizedUrl, scraper, result));
    } catch (DataIntegrityViolationException e) {
      // Concurrent first-track for the same URL — return the winner.
      return productRepository.findByNormalizedUrl(normalizedUrl)
          .orElseThrow(() -> e);
    }
  }

  private Product persistNewProduct(
      String originalUrl, String normalizedUrl, Scraper scraper, ScrapeResult result) {
    return productRepository.findByNormalizedUrl(normalizedUrl)
        .map(existing -> backfillVariantFieldsIfMissing(existing, normalizedUrl, result))
        .orElseGet(() -> {
          String colorCode = firstNonBlank(result.colorCode(), urlNormalizer.colorCode(normalizedUrl));
          String sizeCode = firstNonBlank(result.sizeCode(), urlNormalizer.sizeCode(normalizedUrl));
          String colorName = firstNonBlank(
              result.colorName(),
              uniqloCatalog.colorDisplayName(colorCode).orElse(null));
          String sizeName = firstNonBlank(
              result.sizeName(),
              uniqloCatalog.sizeDisplayName(sizeCode).orElse(null));

          Product product = Product.builder()
              .name(result.name())
              .normalizedUrl(normalizedUrl)
              .originalUrl(originalUrl)
              .site(scraper.getSite())
              .lastKnownPrice(result.price())
              .lastKnownStockStatus(result.stockStatus())
              .thumbnailUrl(result.thumbnailUrl())
              .colorCode(colorCode)
              .colorName(colorName)
              .sizeCode(sizeCode)
              .sizeName(sizeName)
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

  /**
   * Older rows may have colorCode/sizeCode only in {@code normalized_url} (pre-V8 columns)
   * or after a scrape that matched SKU but left names empty. Fill gaps so the API can display them.
   */
  private Product backfillVariantFieldsIfMissing(Product product, String normalizedUrl) {
    return backfillVariantFieldsIfMissing(product, normalizedUrl, null);
  }

  private Product backfillVariantFieldsIfMissing(
      Product product, String normalizedUrl, ScrapeResult result) {
    boolean dirty = false;

    String colorCode = product.getColorCode();
    if (isBlank(colorCode)) {
      colorCode = result != null ? firstNonBlank(result.colorCode(), null) : null;
      colorCode = firstNonBlank(colorCode, urlNormalizer.colorCode(normalizedUrl));
      if (!isBlank(colorCode)) {
        product.setColorCode(colorCode);
        dirty = true;
      }
    }

    String sizeCode = product.getSizeCode();
    if (isBlank(sizeCode)) {
      sizeCode = result != null ? firstNonBlank(result.sizeCode(), null) : null;
      sizeCode = firstNonBlank(sizeCode, urlNormalizer.sizeCode(normalizedUrl));
      if (!isBlank(sizeCode)) {
        product.setSizeCode(sizeCode);
        dirty = true;
      }
    }

    if (isBlank(product.getColorName())) {
      String colorName = result != null ? result.colorName() : null;
      if (isBlank(colorName)) {
        colorName = uniqloCatalog.colorDisplayName(product.getColorCode()).orElse(null);
      }
      if (!isBlank(colorName)) {
        product.setColorName(colorName);
        dirty = true;
      }
    }

    if (isBlank(product.getSizeName())) {
      String sizeName = result != null ? result.sizeName() : null;
      if (isBlank(sizeName)) {
        sizeName = uniqloCatalog.sizeDisplayName(product.getSizeCode()).orElse(null);
      }
      if (!isBlank(sizeName)) {
        product.setSizeName(sizeName);
        dirty = true;
      }
    }

    return dirty ? productRepository.save(product) : product;
  }

  private static String firstNonBlank(String primary, String fallback) {
    if (!isBlank(primary)) {
      return primary;
    }
    return isBlank(fallback) ? null : fallback;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
