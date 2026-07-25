package com.allan.price_watch.trackeditem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.allan.price_watch.TestcontainersConfiguration;
import com.allan.price_watch.auth.entity.User;
import com.allan.price_watch.auth.repository.UserRepository;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.entity.Site;
import com.allan.price_watch.product.entity.StockStatus;
import com.allan.price_watch.product.repository.ProductRepository;
import com.allan.price_watch.trackeditem.entity.TrackedItem;
import com.allan.price_watch.trackeditem.entity.TrackedItemStatus;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

/**
 * Repository-layer tests against real Postgres (via Testcontainers).
 * Verifies custom JPQL queries that the scheduler and manual-check rely on.
 *
 * <p>Requires Docker. Skipped cleanly when Docker is unavailable.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@EnabledIf("com.allan.price_watch.PriceWatchApplicationTests#dockerAvailable")
class TrackedItemRepositoryTest {

  @Autowired TrackedItemRepository trackedItemRepository;
  @Autowired ProductRepository productRepository;
  @Autowired UserRepository userRepository;

  private User user;
  private Product healthyProduct;
  private Product unhealthyProduct;
  private Product pausedProduct;

  @BeforeEach
  void seedData() {
    user = userRepository.saveAndFlush(User.builder()
        .email("repo-test-" + System.nanoTime() + "@test.com")
        .passwordHash("{noop}test")
        .build());

    healthyProduct = productRepository.saveAndFlush(Product.builder()
        .name("Healthy Product")
        .normalizedUrl("https://www.uniqlo.com/ph/en/products/E001?colorCode=COL09&sizeCode=SMA003")
        .originalUrl("https://www.uniqlo.com/ph/en/products/E001")
        .site(Site.UNIQLO)
        .lastKnownPrice(new BigDecimal("990.00"))
        .lastKnownStockStatus(StockStatus.IN_STOCK)
        .consecutiveFailures(0)
        .lastCheckedAt(Instant.now())
        .build());

    unhealthyProduct = productRepository.saveAndFlush(Product.builder()
        .name("Unhealthy Product")
        .normalizedUrl("https://www.uniqlo.com/ph/en/products/E002?colorCode=COL09&sizeCode=SMA003")
        .originalUrl("https://www.uniqlo.com/ph/en/products/E002")
        .site(Site.UNIQLO)
        .lastKnownPrice(new BigDecimal("590.00"))
        .lastKnownStockStatus(StockStatus.IN_STOCK)
        .consecutiveFailures(10)
        .lastCheckedAt(Instant.now())
        .build());

    pausedProduct = productRepository.saveAndFlush(Product.builder()
        .name("Paused Product")
        .normalizedUrl("https://www.uniqlo.com/ph/en/products/E003?colorCode=COL09&sizeCode=SMA003")
        .originalUrl("https://www.uniqlo.com/ph/en/products/E003")
        .site(Site.UNIQLO)
        .lastKnownPrice(new BigDecimal("390.00"))
        .lastKnownStockStatus(StockStatus.OUT_OF_STOCK)
        .consecutiveFailures(0)
        .lastCheckedAt(Instant.now())
        .build());

    trackedItemRepository.saveAndFlush(TrackedItem.builder()
        .user(user).product(healthyProduct)
        .status(TrackedItemStatus.ACTIVE)
        .priceThreshold(new BigDecimal("800"))
        .notifyOnRestockOnly(false)
        .build());

    trackedItemRepository.saveAndFlush(TrackedItem.builder()
        .user(user).product(unhealthyProduct)
        .status(TrackedItemStatus.ACTIVE)
        .priceThreshold(null)
        .notifyOnRestockOnly(false)
        .build());

    trackedItemRepository.saveAndFlush(TrackedItem.builder()
        .user(user).product(pausedProduct)
        .status(TrackedItemStatus.PAUSED)
        .priceThreshold(null)
        .notifyOnRestockOnly(true)
        .build());
  }

  // ─── findDistinctActiveHealthyProductIds ──────────────────────────────────

  @Test
  void activeHealthyQueryExcludesUnhealthyAndPaused() {
    List<UUID> ids = trackedItemRepository.findDistinctActiveHealthyProductIds(5);

    assertTrue(ids.contains(healthyProduct.getId()),
        "healthy ACTIVE product should be included");
    assertFalse(ids.contains(unhealthyProduct.getId()),
        "unhealthy product (failures >= threshold) should be excluded");
    assertFalse(ids.contains(pausedProduct.getId()),
        "PAUSED product should be excluded");
  }

  // ─── findDistinctActiveProductIdsByUserId ─────────────────────────────────

  @Test
  void activeByUserIncludesUnhealthyButExcludesPaused() {
    List<UUID> ids = trackedItemRepository.findDistinctActiveProductIdsByUserId(user.getId());

    assertTrue(ids.contains(healthyProduct.getId()));
    assertTrue(ids.contains(unhealthyProduct.getId()),
        "manual check-all includes unhealthy ACTIVE items");
    assertFalse(ids.contains(pausedProduct.getId()),
        "PAUSED items excluded from check-all");
  }

  @Test
  void activeByUserReturnsEmptyForUnknownUser() {
    List<UUID> ids = trackedItemRepository.findDistinctActiveProductIdsByUserId(UUID.randomUUID());
    assertTrue(ids.isEmpty());
  }

  // ─── findByIdAndUserId (ownership) ────────────────────────────────────────

  @Test
  void findByIdAndUserIdEnforcesOwnership() {
    Optional<TrackedItem> owned = trackedItemRepository.findByIdAndUserId(
        trackedItemRepository.findAll().get(0).getId(), user.getId());
    assertTrue(owned.isPresent());

    Optional<TrackedItem> notOwned = trackedItemRepository.findByIdAndUserId(
        trackedItemRepository.findAll().get(0).getId(), UUID.randomUUID());
    assertTrue(notOwned.isEmpty(), "different user must not see another user's item");
  }

  // ─── countByUserId ────────────────────────────────────────────────────────

  @Test
  void countByUserIdReturnsTotal() {
    assertEquals(3, trackedItemRepository.countByUserId(user.getId()));
    assertEquals(0, trackedItemRepository.countByUserId(UUID.randomUUID()));
  }

  // ─── unhealthy count ──────────────────────────────────────────────────────

  @Test
  void unhealthyCountUsesThreshold() {
    assertEquals(1,
        trackedItemRepository.countByUserIdAndProduct_ConsecutiveFailuresGreaterThanEqual(
            user.getId(), 5));
    assertEquals(0,
        trackedItemRepository.countByUserIdAndProduct_ConsecutiveFailuresGreaterThanEqual(
            user.getId(), 20));
  }
}
