package com.allan.price_watch.scraper.lock;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Redis lock so only one worker scrapes a given product at a time. */
@Service
public class ProductLockService {

  private static final Duration LOCK_TTL = Duration.ofSeconds(60);
  private static final String KEY_PREFIX = "product-lock:";
  private static final String LOCKED_VALUE = "locked";

  private final StringRedisTemplate redisTemplate;

  public ProductLockService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /** Tries to acquire a short-lived lock for the product. */
  public boolean tryLock(UUID productId) {
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(productId), LOCKED_VALUE, LOCK_TTL);
    return Boolean.TRUE.equals(acquired);
  }

  /** Releases the product lock. */
  public void unlock(UUID productId) {
    redisTemplate.delete(key(productId));
  }

  private String key(UUID productId) {
    return KEY_PREFIX + productId;
  }
}
