package com.allan.price_watch.product;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class ProductService {

  private final ProductRepository productRepository;
  private final PriceHistoryRepository priceHistoryRepository;
  private final UrlNormalizer urlNormalizer;
  private final ScraperFactory scraperFactory;

  public ProductService(
      ProductRepository productRepository,
      PriceHistoryRepository priceHistoryRepository,
      UrlNormalizer urlNormalizer,
      ScraperFactory scraperFactory) {
    this.productRepository = productRepository;
    this.priceHistoryRepository = priceHistoryRepository;
    this.urlNormalizer = urlNormalizer;
    this.scraperFactory = scraperFactory;
  }

  /**
   * The dedup + first-fetch step from plan §5. An existing {@code Product}
   * row is reused as-is — a second user tracking the same product doesn't
   * trigger a re-scrape, that's the scheduler's regular cron's job. A
   * brand new URL triggers exactly one synchronous scrape right here,
   * since the caller (ultimately POST /tracked-items) needs a real
   * price/stock value to return immediately rather than "unknown, check
   * back later."
   */
  @Transactional
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

  /**
   * {@code from}/{@code to} are optional and only take effect together —
   * passing just one of the two is treated the same as passing neither
   * (falls back to the unfiltered, newest-first query) rather than
   * erroring. Documented behavior, not an oversight: keeps the endpoint
   * lenient for a v1 scope where nobody's built a date-range picker UI
   * against it yet.
   */
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

  private Product createFromScrape(String originalUrl, String normalizedUrl, URI uri) {
    Scraper scraper = scraperFactory.resolve(uri);
    ScrapeResult result = scraper.fetch(uri);

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

    return productRepository.save(product);
  }
}