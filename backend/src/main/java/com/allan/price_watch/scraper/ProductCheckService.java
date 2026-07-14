package com.allan.price_watch.scraper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.allan.price_watch.common.exception.ManualCheckRateLimitedException;
import com.allan.price_watch.common.exception.ResourceNotFoundException;
import com.allan.price_watch.product.ProductHealth;
import com.allan.price_watch.product.dto.ManualCheckAllResponse;
import com.allan.price_watch.product.dto.ProductResponse;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.repository.ProductRepository;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

import static com.allan.price_watch.config.RabbitMqConfig.EXCHANGE;
import static com.allan.price_watch.config.RabbitMqConfig.PRODUCT_CHECK_QUEUE;

/**
 * Enqueues product scrapes for the scheduler, startup catch-up, and manual "check now".
 * Manual checks are rate-limited per product and per user via Redis.
 */
@Service
public class ProductCheckService {

  private static final Logger log = LoggerFactory.getLogger(ProductCheckService.class);

  private static final String PRODUCT_COOLDOWN_KEY = "manual-check:product:";
  private static final String USER_HOUR_KEY = "manual-check:user:";
  private static final String CHECK_ALL_KEY = "manual-check:all:";

  private final TrackedItemRepository trackedItemRepository;
  private final ProductRepository productRepository;
  private final RabbitTemplate rabbitTemplate;
  private final StringRedisTemplate redisTemplate;
  private final Duration productCooldown;
  private final int userHourlyLimit;
  private final Duration checkAllCooldown;
  private final Duration startupStaleAfter;

  public ProductCheckService(
      TrackedItemRepository trackedItemRepository,
      ProductRepository productRepository,
      RabbitTemplate rabbitTemplate,
      StringRedisTemplate redisTemplate,
      @Value("${app.scraper.manual-check.product-cooldown:15m}") Duration productCooldown,
      @Value("${app.scraper.manual-check.user-hourly-limit:20}") int userHourlyLimit,
      @Value("${app.scraper.manual-check.check-all-cooldown:1h}") Duration checkAllCooldown,
      @Value("${app.scraper.startup-stale-after:1h}") Duration startupStaleAfter) {
    this.trackedItemRepository = trackedItemRepository;
    this.productRepository = productRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.redisTemplate = redisTemplate;
    this.productCooldown = productCooldown;
    this.userHourlyLimit = Math.max(1, userHourlyLimit);
    this.checkAllCooldown = checkAllCooldown;
    this.startupStaleAfter = startupStaleAfter;
  }

  /** Publishes one product.check message (scheduler / catch-up / manual). */
  public void enqueue(UUID productId) {
    rabbitTemplate.convertAndSend(EXCHANGE, PRODUCT_CHECK_QUEUE, productId);
  }

  /** Enqueues every healthy product that has at least one ACTIVE tracker. */
  public int enqueueAllHealthyActive() {
    List<UUID> ids = trackedItemRepository.findDistinctActiveHealthyProductIds(
        ProductHealth.UNHEALTHY_FAILURE_THRESHOLD);
    for (UUID id : ids) {
      enqueue(id);
    }
    return ids.size();
  }

  /**
   * Startup catch-up: enqueue healthy active products that were never checked or are older
   * than {@code app.scraper.startup-stale-after}.
   */
  public int enqueueStaleHealthyActive() {
    Instant cutoff = Instant.now().minus(startupStaleAfter);
    List<UUID> ids = trackedItemRepository.findDistinctActiveHealthyStaleProductIds(
        ProductHealth.UNHEALTHY_FAILURE_THRESHOLD, cutoff);
    for (UUID id : ids) {
      enqueue(id);
    }
    log.info("Startup catch-up enqueued {} stale product check(s)", ids.size());
    return ids.size();
  }

  /**
   * Queues an immediate scrape for a product the user tracks.
   * Rate limits: one manual check per product per cooldown window, and
   * {@code user-hourly-limit} manual checks per user per rolling hour.
   */
  public ProductResponse requestManualCheck(UUID userId, UUID productId) {
    if (!trackedItemRepository.existsByUserIdAndProductId(userId, productId)) {
      throw new ResourceNotFoundException("No product found with id " + productId);
    }
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new ResourceNotFoundException("No product found with id " + productId));

    claimManualCheckSlots(userId, productId);
    enqueue(productId);
    log.debug("Manual check queued for product {} by user {}", productId, userId);
    return ProductResponse.from(product);
  }

  /**
   * Queues scrapes for every product the user tracks as ACTIVE (including unhealthy).
   * Rate limited once per {@code check-all-cooldown} (default 1 hour).
   */
  public ManualCheckAllResponse requestManualCheckAll(UUID userId) {
    claimCheckAllSlot(userId);
    List<UUID> ids = trackedItemRepository.findDistinctActiveProductIdsByUserId(userId);
    for (UUID id : ids) {
      enqueue(id);
    }
    log.info("Manual check-all queued {} product(s) for user {}", ids.size(), userId);
    return new ManualCheckAllResponse(ids.size());
  }

  private void claimCheckAllSlot(UUID userId) {
    try {
      Boolean ok = redisTemplate.opsForValue()
          .setIfAbsent(CHECK_ALL_KEY + userId, "1", checkAllCooldown);
      if (!Boolean.TRUE.equals(ok)) {
        throw new ManualCheckRateLimitedException(
            "You already refreshed all products recently. Try again in up to "
                + formatDuration(checkAllCooldown) + ".");
      }
    } catch (ManualCheckRateLimitedException e) {
      throw e;
    } catch (RuntimeException e) {
      log.warn("Check-all rate limit store unavailable: {}", e.getMessage());
      throw new ManualCheckRateLimitedException(
          "Check rate limiting is temporarily unavailable. Try again shortly.");
    }
  }

  private void claimManualCheckSlots(UUID userId, UUID productId) {
    try {
      Boolean productOk = redisTemplate.opsForValue()
          .setIfAbsent(PRODUCT_COOLDOWN_KEY + productId, "1", productCooldown);
      if (!Boolean.TRUE.equals(productOk)) {
        throw new ManualCheckRateLimitedException(
            "This product was checked recently. Try again in up to "
                + formatDuration(productCooldown) + ".");
      }

      String userKey = USER_HOUR_KEY + userId;
      Long count = redisTemplate.opsForValue().increment(userKey);
      if (count != null && count == 1L) {
        redisTemplate.expire(userKey, Duration.ofHours(1));
      }
      if (count != null && count > userHourlyLimit) {
        // Release product cooldown so a failed user-limit claim does not burn the product slot.
        redisTemplate.delete(PRODUCT_COOLDOWN_KEY + productId);
        throw new ManualCheckRateLimitedException(
            "Manual check limit reached (" + userHourlyLimit + " per hour). Try again later.");
      }
    } catch (ManualCheckRateLimitedException e) {
      throw e;
    } catch (RuntimeException e) {
      // Fail closed: without Redis we cannot enforce limits against Uniqlo.
      log.warn("Manual-check rate limit store unavailable: {}", e.getMessage());
      throw new ManualCheckRateLimitedException(
          "Check rate limiting is temporarily unavailable. Try again shortly.");
    }
  }

  private static String formatDuration(Duration d) {
    long minutes = d.toMinutes();
    if (minutes > 0 && d.minusMinutes(minutes).isZero()) {
      return minutes + (minutes == 1 ? " minute" : " minutes");
    }
    return d.toString().substring(2).toLowerCase();
  }
}
