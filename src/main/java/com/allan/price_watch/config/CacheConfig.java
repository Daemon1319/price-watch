package com.allan.price_watch.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * {@code @EnableCaching} is all that's needed here — Spring Boot
 * auto-configures a Redis-backed {@code RedisCacheManager} automatically
 * once it sees {@code spring-boot-starter-data-redis} on the classpath
 * (already present), no manual {@code CacheManager} bean required. The
 * actual TTL (10 minutes, per plan's dashboard caching requirement) is set
 * via {@code spring.cache.redis.time-to-live} in {@code application.yml}
 * rather than here, so it's tunable without a code change.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}