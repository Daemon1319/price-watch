package com.allan.price_watch.scheduler;

import java.util.List;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import static com.allan.price_watch.config.RabbitMqConfig.EXCHANGE;
import static com.allan.price_watch.config.RabbitMqConfig.PRODUCT_CHECK_QUEUE;

/**
 * The cron entry point for plan §5's whole recheck cycle: find every
 * product with at least one active tracker, enqueue a {@code product.check}
 * job for each. Everything else — locking, throttling, the actual scrape,
 * updating the product, deciding whether to notify — happens downstream in
 * {@code ScrapeWorker}; this class's only job is "who needs checking right
 * now."
 *
 * <p>{@code @SchedulerLock} means only one app instance actually runs this
 * per cron tick — without it, running two instances of this app (for
 * uptime, say) would double-enqueue every product on every tick.
 * {@code lockAtLeastFor} is set even though this method usually finishes
 * in well under a second — it guards against clock skew between instances
 * causing the lock to be considered "expired" and re-acquired by a second
 * instance moments after the first one already ran.
 */
@Component
public class ProductCheckScheduler {

  private final TrackedItemRepository trackedItemRepository;
  private final RabbitTemplate rabbitTemplate;

  public ProductCheckScheduler(TrackedItemRepository trackedItemRepository, RabbitTemplate rabbitTemplate) {
    this.trackedItemRepository = trackedItemRepository;
    this.rabbitTemplate = rabbitTemplate;
  }

  @Scheduled(cron = "${app.scheduler.product-check-cron}")
  @SchedulerLock(name = "productCheckScheduler", lockAtLeastFor = "PT30S", lockAtMostFor = "PT10M")
  public void enqueueChecks() {
    List<UUID> productIds = trackedItemRepository.findDistinctActiveProductIds();

    for (UUID productId : productIds) {
      rabbitTemplate.convertAndSend(EXCHANGE, PRODUCT_CHECK_QUEUE, productId);
    }
  }
}