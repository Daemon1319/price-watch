package com.allan.price_watch.scraper.lock;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Redis lock so only one worker scrapes a given product at a time. */
@Service
public class ProductLockService {

  private static final Logger log = LoggerFactory.getLogger(ProductLockService.class);

  private static final Duration LOCK_TTL = Duration.ofSeconds(60);
  private static final String KEY_PREFIX = "product-lock:";
  private static final String LOCKED_VALUE = "locked";

  private final StringRedisTemplate redisTemplate;

  public ProductLockService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Tries to acquire a short-lived lock for the product.
   * If Redis is unavailable, fails open (allows scrape) — may double-scrape under multi-worker.
   */
  public boolean tryLock(UUID productId) {
    try {
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(productId), LOCKED_VALUE, LOCK_TTL);
      return Boolean.TRUE.equals(acquired);
    } catch (RuntimeException e) {
      log.warn("Product lock unavailable for {}; allowing scrape: {}", productId, e.getMessage());
      return true;
    }
  }

  /** Releases the product lock. */
  public void unlock(UUID productId) {
    try {
      redisTemplate.delete(key(productId));
    } catch (RuntimeException e) {
      log.warn("Product unlock failed for {}: {}", productId, e.getMessage());
    }
  }

  private String key(UUID productId) {
    return KEY_PREFIX + productId;
  }
}
