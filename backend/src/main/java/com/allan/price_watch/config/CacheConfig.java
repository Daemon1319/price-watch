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

/** Redis-backed Spring Cache setup for dashboard summaries and similar. */
@Configuration
@EnableCaching
public class CacheConfig {

  /** Default Redis cache config: TTL, string keys, Jackson JSON values. */
  @Bean
  public RedisCacheConfiguration redisCacheConfiguration(
      @Value("${spring.cache.redis.time-to-live:10m}") Duration ttl) {

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
