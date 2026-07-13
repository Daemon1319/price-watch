package com.allan.price_watch;

import org.springframework.boot.SpringApplication;

/**
 * Local / IDE entrypoint that boots the real app against Testcontainers
 * (Postgres, Redis, RabbitMQ) instead of Docker Compose.
 *
 * <p>Run with the <strong>test</strong> classpath so {@code src/test/resources/application.yaml}
 * applies (compose disabled, test JWT, mail stub). Includes Flyway through V8
 * (product variants) and {@code classpath:scraper/uniqlo/catalog.json}.
 *
 * <p>IDE: run this class’s {@code main}. CLI:
 * {@code ./mvnw spring-boot:test-run} (Boot 3.1+) or execute the test-compiled main.
 */
public class TestPriceWatchApplication {

  public static void main(String[] args) {
    SpringApplication.from(PriceWatchApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
