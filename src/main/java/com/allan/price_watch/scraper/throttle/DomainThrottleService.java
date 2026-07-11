package com.allan.price_watch.scraper.throttle;

import java.net.URI;
import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed minimum-delay-between-requests guard, per domain (e.g.
 * {@code uniqlo.com}), shared across every app instance and every
 * {@code ScrapeWorker} thread — an in-memory-only throttle wouldn't
 * coordinate across multiple running instances, which defeats the point
 * of being a good citizen toward the target sites.
 *
 * <p>Same SETNX-with-TTL primitive as {@code ProductLockService}, but
 * here the TTL itself <em>is</em> the throttle: a domain key existing
 * means "a request to this domain happened within the last
 * {@code MIN_DELAY_BETWEEN_REQUESTS}," full stop. This class only answers
 * "can I go right now" — retry/backoff policy when the answer is "no"
 * deliberately lives in {@code ScrapeWorker}, not here, so the throttle
 * primitive stays simple and independently testable.
 */
@Service
public class DomainThrottleService {

  // Plan suggests 3–5s between requests to the same domain.
  private static final Duration MIN_DELAY_BETWEEN_REQUESTS = Duration.ofSeconds(3);
  private static final String KEY_PREFIX = "domain-throttle:";

  private final StringRedisTemplate redisTemplate;

  public DomainThrottleService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Checking and marking are the same atomic call on purpose — a separate
   * "is it allowed" read followed by a "mark it" write would let two
   * concurrent callers both observe "allowed" before either one marks the
   * domain as just-requested.
   */
  public boolean tryAcquire(URI url) {
    String domain = url.getHost();
    Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent(KEY_PREFIX + domain, "1", MIN_DELAY_BETWEEN_REQUESTS);
    return Boolean.TRUE.equals(acquired);
  }
}