package com.allan.price_watch.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import com.allan.price_watch.common.exception.VariantRequiredException;
import com.allan.price_watch.product.dto.ProductVariantOption;
import com.allan.price_watch.product.dto.ProductVariantsResponse;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.Site;
import com.allan.price_watch.product.entity.StockStatus;
import com.allan.price_watch.product.repository.ProductRepository;
import com.allan.price_watch.scraper.ScrapeResult;
import com.allan.price_watch.scraper.ScraperFactory;
import com.allan.price_watch.scraper.site.UniqloCatalog;
import com.allan.price_watch.scraper.site.UniqloScraper;
import com.allan.price_watch.trackeditem.repository.PriceHistoryRepository;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

import tools.jackson.databind.json.JsonMapper;

/** Unit tests for Uniqlo color/size requirements on product create / variant list. */
class ProductServiceVariantTest {

  private ProductRepository productRepository;
  private PriceHistoryRepository priceHistoryRepository;
  private ScraperFactory scraperFactory;
  private UniqloScraper uniqloScraper;
  private ProductService productService;

  @BeforeEach
  void setUp() {
    productRepository = mock(ProductRepository.class);
    priceHistoryRepository = mock(PriceHistoryRepository.class);
    TrackedItemRepository trackedItemRepository = mock(TrackedItemRepository.class);
    scraperFactory = mock(ScraperFactory.class);
    uniqloScraper = mock(UniqloScraper.class);

    UniqloCatalog catalog = UniqloCatalog.fromClasspath(
        new JsonMapper(), UniqloCatalog.CLASSPATH_LOCATION);
    UrlNormalizer urlNormalizer = new UrlNormalizer(catalog);

    TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
      org.springframework.transaction.support.TransactionCallback<?> callback =
          invocation.getArgument(0);
      return callback.doInTransaction(null);
    });

    productService = new ProductService(
        productRepository,
        priceHistoryRepository,
        trackedItemRepository,
        urlNormalizer,
        catalog,
        scraperFactory,
        transactionTemplate);

    when(uniqloScraper.getSite()).thenReturn(Site.UNIQLO);
    when(uniqloScraper.supports(any(URI.class))).thenReturn(true);
    when(scraperFactory.resolve(any(URI.class))).thenReturn(uniqloScraper);
  }

  @Test
  void findOrCreateRequiresColorAndSize() {
    assertThrows(
        VariantRequiredException.class,
        () -> productService.findOrCreateByUrl(
            "https://www.uniqlo.com/ph/en/products/E482465-000"));

    verify(productRepository, never()).findByNormalizedUrl(any());
  }

  @Test
  void findOrCreateAcceptsVariantInBody() {
    String expectedUrl =
        "https://www.uniqlo.com/ph/en/products/E482465-000?colorCode=COL60&sizeCode=SMA003";
    Product existing = Product.builder()
        .normalizedUrl(expectedUrl)
        .originalUrl(expectedUrl)
        .site(Site.UNIQLO)
        .colorCode("COL60")
        .sizeCode("SMA003")
        .lastKnownStockStatus(StockStatus.IN_STOCK)
        .build();

    when(productRepository.findByNormalizedUrl(expectedUrl)).thenReturn(Optional.of(existing));

    Product result = productService.findOrCreateByUrl(
        "https://www.uniqlo.com/ph/en/products/E482465-000",
        "COL60",
        "SMA003");

    assertEquals(existing, result);
    assertEquals("COL60", result.getColorCode());
    assertEquals("SMA003", result.getSizeCode());
  }

  @Test
  void findOrCreateAcceptsVariantInUrlQuery() {
    String expectedUrl =
        "https://www.uniqlo.com/ph/en/products/E482465-000?colorCode=COL60&sizeCode=SMA003";
    Product existing = Product.builder()
        .normalizedUrl(expectedUrl)
        .originalUrl(expectedUrl)
        .site(Site.UNIQLO)
        .lastKnownStockStatus(StockStatus.IN_STOCK)
        .build();

    when(productRepository.findByNormalizedUrl(expectedUrl)).thenReturn(Optional.of(existing));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    Product result = productService.findOrCreateByUrl(
        "https://www.uniqlo.com/ph/en/products/E482465-000?colorCode=COL60&sizeCode=SMA003");

    assertEquals(existing, result);
    // Pre-V8 / incomplete rows: codes live only in the URL until backfilled.
    assertEquals("COL60", result.getColorCode());
    assertEquals("SMA003", result.getSizeCode());
    assertEquals("S", result.getSizeName());
  }

  @Test
  void findOrCreateBackfillsColor69FromUrl() {
    String expectedUrl =
        "https://www.uniqlo.com/ph/en/products/E485455-000?colorCode=COL69&sizeCode=SMA003";
    Product existing = Product.builder()
        .normalizedUrl(expectedUrl)
        .originalUrl(expectedUrl)
        .site(Site.UNIQLO)
        .lastKnownStockStatus(StockStatus.IN_STOCK)
        .build();

    when(productRepository.findByNormalizedUrl(expectedUrl)).thenReturn(Optional.of(existing));
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

    Product result = productService.findOrCreateByUrl(expectedUrl);

    assertEquals("COL69", result.getColorCode());
    assertEquals("Navy", result.getColorName());
    assertEquals("SMA003", result.getSizeCode());
    assertEquals("S", result.getSizeName());
  }

  @Test
  void listVariantsDelegatesToUniqloScraper() {
    ProductVariantsResponse response = new ProductVariantsResponse(
        "Airism Tee",
        "E482465-000",
        "https://www.uniqlo.com/ph/en/products/E482465-000",
        List.of(new ProductVariantsResponse.ColorOption("COL60", "LIGHT BLUE", "60")),
        List.of(new ProductVariantsResponse.SizeOption("SMA003", "S", "003")),
        List.of(new ProductVariantOption(
            "COL60",
            "LIGHT BLUE",
            "SMA003",
            "S",
            new BigDecimal("990"),
            StockStatus.IN_STOCK,
            null)));

    when(uniqloScraper.listVariants(any(URI.class))).thenReturn(response);

    ProductVariantsResponse result = productService.listVariants(
        "https://www.uniqlo.com/ph/en/products/E482465-000?utm_source=x");

    assertEquals("Airism Tee", result.productName());
    assertEquals(1, result.variants().size());
    assertEquals("COL60", result.variants().get(0).colorCode());
    verify(uniqloScraper).listVariants(any(URI.class));
  }

  @Test
  void createFromScrapePersistsVariantFields() {
    String expectedUrl =
        "https://www.uniqlo.com/ph/en/products/E482465-000?colorCode=COL60&sizeCode=SMA003";

    when(productRepository.findByNormalizedUrl(expectedUrl)).thenReturn(Optional.empty());

    ScrapeResult scrape = new ScrapeResult(
        "Airism Tee",
        new BigDecimal("990.00"),
        StockStatus.IN_STOCK,
        "https://image.uniqlo.com/x.jpg",
        "COL60",
        "LIGHT BLUE",
        "SMA003",
        "S");
    when(uniqloScraper.fetch(any(URI.class))).thenReturn(scrape);
    when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    when(priceHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Product created = productService.findOrCreateByUrl(
        "https://www.uniqlo.com/ph/en/products/E482465-000",
        "60",
        "SMA003");

    assertEquals("COL60", created.getColorCode());
    assertEquals("LIGHT BLUE", created.getColorName());
    assertEquals("SMA003", created.getSizeCode());
    assertEquals("S", created.getSizeName());
    assertEquals(expectedUrl, created.getNormalizedUrl());
    verify(uniqloScraper).fetch(any(URI.class));
  }
}
