package com.allan.price_watch.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import com.allan.price_watch.security.RateLimitFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;

/** Redis Bucket4j beans for shared API and auth rate-limit counters. */
@Configuration
public class RateLimitConfig {

  private RedisClient redisClient;
  private StatefulRedisConnection<String, byte[]> connection;

  /** Proxy manager that stores rate-limit buckets in Redis. */
  @Bean
  public ProxyManager<String> rateLimitProxyManager(RedisConnectionFactory connectionFactory) {
    RedisURI uri = resolveRedisUri(connectionFactory);
    this.redisClient = RedisClient.create(uri);
    this.connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

    return Bucket4jLettuce.casBasedBuilder(connection)
        .expirationAfterWrite(
            ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
        .build();
  }

  /**
   * Mirrors Spring's Redis factory (from {@code spring.data.redis.url} or host/port props)
   * so Bucket4j uses the same TLS/username/password as the rest of the app.
   */
  private static RedisURI resolveRedisUri(RedisConnectionFactory connectionFactory) {
    if (!(connectionFactory instanceof LettuceConnectionFactory lettuce)) {
      return RedisURI.builder().withHost("localhost").withPort(6379).build();
    }

    RedisURI.Builder builder = RedisURI.builder()
        .withHost(lettuce.getHostName())
        .withPort(lettuce.getPort())
        .withSsl(lettuce.isUseSsl());

    RedisStandaloneConfiguration standalone = lettuce.getStandaloneConfiguration();
    String username = standalone != null ? standalone.getUsername() : null;
    String password = lettuce.getPassword();

    if (username != null && !username.isBlank()) {
      char[] pwd = password != null ? password.toCharArray() : new char[0];
      builder.withAuthentication(username, pwd);
    } else if (password != null && !password.isBlank()) {
      builder.withPassword(password.toCharArray());
    }

    if (lettuce.getDatabase() > 0) {
      builder.withDatabase(lettuce.getDatabase());
    }
    return builder.build();
  }

  /** General API rate-limit capacity and refill. */
  @Bean(name = "apiRateLimitConfiguration")
  public BucketConfiguration apiRateLimitConfiguration(
      @Value("${app.rate-limit.capacity:60}") long capacity,
      @Value("${app.rate-limit.refill-per-minute:60}") long refillPerMinute) {

    return bucket(capacity, refillPerMinute);
  }

  /** Stricter bucket for login and register. */
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

  /** Disables Boot servlet registration so the filter runs only in Security. */
  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
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
