package com.allan.price_watch.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import com.allan.price_watch.security.RateLimitFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;

/**
 * Redis-backed Bucket4j setup so rate-limit counters are shared across app
 * instances. Uses a dedicated Lettuce connection with a string/byte codec
 * (Bucket4j's requirement) rather than reusing Spring Data Redis's
 * connection factory directly.
 *
 * <p>{@code RateLimitFilter} is registered only inside the Security filter
 * chain (after JWT) — the {@link FilterRegistrationBean} below disables
 * Boot's default "every Filter bean is a servlet filter" registration so
 * it doesn't run twice / before auth.
 */
@Configuration
public class RateLimitConfig {

  private RedisClient redisClient;
  private StatefulRedisConnection<String, byte[]> connection;

  @Bean
  public ProxyManager<String> rateLimitProxyManager(RedisConnectionFactory connectionFactory) {
    RedisURI uri = resolveRedisUri(connectionFactory);
    this.redisClient = RedisClient.create(uri);
    this.connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

    return LettuceBasedProxyManager.builderFor(connection)
        .withExpirationStrategy(
            ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
        .build();
  }

  private static RedisURI resolveRedisUri(RedisConnectionFactory connectionFactory) {
    if (connectionFactory instanceof LettuceConnectionFactory lettuce) {
      RedisURI.Builder builder = RedisURI.builder()
          .withHost(lettuce.getHostName())
          .withPort(lettuce.getPort());
      String password = lettuce.getPassword();
      if (password != null && !password.isBlank()) {
        builder.withPassword(password.toCharArray());
      }
      if (lettuce.getDatabase() > 0) {
        builder.withDatabase(lettuce.getDatabase());
      }
      return builder.build();
    }
    return RedisURI.builder().withHost("localhost").withPort(6379).build();
  }

  @Bean(name = "apiRateLimitConfiguration")
  public BucketConfiguration apiRateLimitConfiguration(
      @Value("${app.rate-limit.capacity:60}") long capacity,
      @Value("${app.rate-limit.refill-per-minute:60}") long refillPerMinute) {

    return bucket(capacity, refillPerMinute);
  }

  /**
   * Stricter bucket for {@code POST /auth/login} and {@code /auth/register}
   * so credential stuffing is throttled harder than normal API traffic.
   */
  @Bean(name = "authRateLimitConfiguration")
  public BucketConfiguration authRateLimitConfiguration(
      @Value("${app.rate-limit.auth-capacity:10}") long capacity,
      @Value("${app.rate-limit.auth-refill-per-minute:10}") long refillPerMinute) {

    return bucket(capacity, refillPerMinute);
  }

  private static BucketConfiguration bucket(long capacity, long refillPerMinute) {
    Bandwidth limit = Bandwidth.builder()
        .capacity(capacity)
        .refillGreedy(refillPerMinute, Duration.ofMinutes(1))
        .build();
    return BucketConfiguration.builder().addLimit(limit).build();
  }

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    // Only run via SecurityFilterChain (after JWT), not as a standalone servlet filter.
    registration.setEnabled(false);
    return registration;
  }

  @PreDestroy
  void shutdown() {
    if (connection != null) {
      connection.close();
    }
    if (redisClient != null) {
      redisClient.shutdown();
    }
  }
}
