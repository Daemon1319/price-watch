package com.allan.price_watch.scheduler;

import java.util.List;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.allan.price_watch.product.ProductHealth;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import static com.allan.price_watch.config.RabbitMqConfig.EXCHANGE;
import static com.allan.price_watch.config.RabbitMqConfig.PRODUCT_CHECK_QUEUE;

/** Periodically enqueues scrape jobs for actively tracked healthy products. */
@Component
public class ProductCheckScheduler {

  private final TrackedItemRepository trackedItemRepository;
  private final RabbitTemplate rabbitTemplate;

  public ProductCheckScheduler(TrackedItemRepository trackedItemRepository, RabbitTemplate rabbitTemplate) {
    this.trackedItemRepository = trackedItemRepository;
    this.rabbitTemplate = rabbitTemplate;
  }

  /** Publishes one product.check message per product that needs a recheck. */
  @Scheduled(cron = "${app.scheduler.product-check-cron:0 0 */4 * * *}")
  @SchedulerLock(name = "productCheckScheduler", lockAtLeastFor = "PT30S", lockAtMostFor = "PT10M")
  public void enqueueChecks() {
    List<UUID> productIds = trackedItemRepository.findDistinctActiveHealthyProductIds(
        ProductHealth.UNHEALTHY_FAILURE_THRESHOLD);

    for (UUID productId : productIds) {
      rabbitTemplate.convertAndSend(EXCHANGE, PRODUCT_CHECK_QUEUE, productId);
    }
  }
}
