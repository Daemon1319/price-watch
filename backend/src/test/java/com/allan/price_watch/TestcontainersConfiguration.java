package com.allan.price_watch;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers for {@link PriceWatchApplicationTests} and
 * {@link TestPriceWatchApplication}. Connections are injected via
 * {@link ServiceConnection} (no hard-coded ports).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    // PG 18+ required: schema uses uuidv7() (not available on 16/17).
    return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
  }

  @Bean
  @ServiceConnection
  RabbitMQContainer rabbitContainer() {
    return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-alpine"));
  }

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
  }
}
