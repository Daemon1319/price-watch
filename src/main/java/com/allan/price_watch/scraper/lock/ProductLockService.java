package com.allan.price_watch.scraper.lock;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed per-product lock, so two {@code ScrapeWorker} deliveries
 * (or two app instances) never scrape the same product concurrently.
 * Implemented as plain SETNX-with-TTL via {@code StringRedisTemplate}
 * rather than Redisson — deliberate, per the earlier pom.xml discussion:
 * fewer moving parts for a project this size, and Redisson's watchdog
 * auto-renewal isn't needed here since a scrape is one bounded operation,
 * not a long-held lock. {@code StringRedisTemplate} (not a raw
 * {@code RedisTemplate<String,String>}) is the bean Spring Boot actually
 * auto-configures for exactly this string-in/string-out use case.
 *
 * <p>{@code unlock} deletes unconditionally rather than using a
 * compare-and-delete Lua script to verify the caller still "owns" the
 * lock. Known, accepted simplification: the 60s TTL already bounds the
 * worst case (a lock outliving its holder by at most 60s), and the
 * failure mode of an unconditional delete — releasing a lock that
 * technically already expired and was re-acquired by someone else — is a
 * rare double-scrape of one product, not a correctness or security
 * problem. Revisit with a Lua script if this ever needs stronger
 * guarantees.
 */
@Service
public class ProductLockService {

  private static final Duration LOCK_TTL = Duration.ofSeconds(60);
  private static final String KEY_PREFIX = "product-lock:";
  private static final String LOCKED_VALUE = "locked";

  private final StringRedisTemplate redisTemplate;

  public ProductLockService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Atomic {@code SET key value NX EX ttl} — the single-command form, not
   * a separate SETNX-then-EXPIRE pair. Doing those as two calls would
   * leave a window where a crash between them leaves the lock without a
   * TTL, i.e. permanently held. {@code setIfAbsent(key, value, Duration)}
   * does both in one round trip.
   */
  public boolean tryLock(UUID productId) {
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(productId), LOCKED_VALUE, LOCK_TTL);
    return Boolean.TRUE.equals(acquired);
  }

  public void unlock(UUID productId) {
    redisTemplate.delete(key(productId));
  }

  private String key(UUID productId) {
    return KEY_PREFIX + productId;
  }
}