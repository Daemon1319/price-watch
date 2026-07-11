package com.allan.price_watch.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Explicit Redis {@link CacheManager}. Spring Boot 4 does not always auto-
 * create one just from {@code spring-boot-starter-data-redis} +
 * {@code @EnableCaching}, and {@code ScrapeResultService} injects
 * {@code CacheManager} directly for dashboard eviction after scrapes.
 *
 * <p>Value serializer uses Spring Data Redis 4's Jackson 3 builder
 * ({@link GenericJacksonJsonRedisSerializer#builder()}) — there is no
 * no-arg constructor on this class anymore.
 */
@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public RedisCacheConfiguration redisCacheConfiguration(
      @Value("${spring.cache.redis.time-to-live:10m}") Duration ttl) {

    // Default typing so @Cacheable can round-trip DTOs (not just LinkedHashMap).
    // enableSpringCacheNullValueSupport matches Spring Cache's NullValue marker.
    GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
        .enableSpringCacheNullValueSupport()
        .enableUnsafeDefaultTyping()
        .build();

    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(ttl)
        .disableCachingNullValues()
        .serializeKeysWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
  }

  @Bean
  public CacheManager cacheManager(
      RedisConnectionFactory connectionFactory,
      RedisCacheConfiguration redisCacheConfiguration) {
    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(redisCacheConfiguration)
        .build();
  }
}
