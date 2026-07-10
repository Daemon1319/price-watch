package com.allan.price_watch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * {@code @EnableSchedulerLock} makes {@code @SchedulerLock} available on
 * {@code @Scheduled} methods app-wide — this is what stops
 * {@code ProductCheckScheduler} and {@code OutboxRelay} from double-firing
 * if this app is ever run as more than one instance. {@code defaultLockAtMostFor}
 * is a safety net: if an instance dies mid-run without releasing its lock,
 * ShedLock still won't hold it hostage forever.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulingConfig {

  @Bean
  public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
    return new RedisLockProvider(connectionFactory, "price-watch");
  }
}