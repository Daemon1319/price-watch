package com.allan.price_watch.scraper.throttle;

import java.net.URI;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Enforces a minimum delay between scrapes of the same domain. */
@Service
public class DomainThrottleService {

  private static final Logger log = LoggerFactory.getLogger(DomainThrottleService.class);

  private static final Duration MIN_DELAY_BETWEEN_REQUESTS = Duration.ofSeconds(3);
  private static final String KEY_PREFIX = "domain-throttle:";

  private final StringRedisTemplate redisTemplate;

  public DomainThrottleService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * Claims a throttle slot for the URL's host, or returns false if too soon.
   * If Redis is unavailable, fails open (allows scrape) so a cache blip does not stall the queue.
   */
  public boolean tryAcquire(URI url) {
    String domain = url.getHost();
    try {
      Boolean acquired = redisTemplate.opsForValue()
          .setIfAbsent(KEY_PREFIX + domain, "1", MIN_DELAY_BETWEEN_REQUESTS);
      return Boolean.TRUE.equals(acquired);
    } catch (RuntimeException e) {
      log.warn("Domain throttle unavailable for {}; allowing scrape: {}", domain, e.getMessage());
      return true;
    }
  }
}
