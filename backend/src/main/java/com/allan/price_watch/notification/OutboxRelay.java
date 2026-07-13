package com.allan.price_watch.notification;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.allan.price_watch.notification.dto.NotificationMessage;
import com.allan.price_watch.notification.entity.OutboxEvent;
import com.allan.price_watch.notification.repository.OutboxEventRepository;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import static com.allan.price_watch.config.RabbitMqConfig.EXCHANGE;
import static com.allan.price_watch.config.RabbitMqConfig.NOTIFICATION_SEND_QUEUE;

/** Polls unpublished outbox rows and publishes them to the notification queue. */
@Component
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private final OutboxEventRepository outboxEventRepository;
  private final RabbitTemplate rabbitTemplate;
  private final TransactionTemplate transactionTemplate;
  private final int batchSize;

  public OutboxRelay(
      OutboxEventRepository outboxEventRepository,
      RabbitTemplate rabbitTemplate,
      TransactionTemplate transactionTemplate,
      @Value("${app.outbox.batch-size:50}") int batchSize) {
    this.outboxEventRepository = outboxEventRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.transactionTemplate = transactionTemplate;
    this.batchSize = batchSize;
  }

  /** Relays a batch of unpublished outbox events to RabbitMQ. */
  @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
  @SchedulerLock(name = "outboxRelay", lockAtLeastFor = "PT2S", lockAtMostFor = "PT1M")
  public void relay() {
    List<OutboxEvent> batch = outboxEventRepository
        .findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, batchSize));

    if (batch.isEmpty()) {
      return;
    }

    int relayed = 0;
    for (OutboxEvent event : batch) {
      try {
        transactionTemplate.executeWithoutResult(status -> publishAndMark(event));
        relayed++;
      } catch (RuntimeException e) {
        log.warn("Failed to relay outbox event {}: {}", event.getId(), e.getMessage());
        // Stop this tick; next poll retries remaining rows.
        break;
      }
    }

    if (relayed > 0) {
      log.debug("Relayed {} outbox event(s) to {}", relayed, NOTIFICATION_SEND_QUEUE);
    }
  }

  /** Publishes one event and marks it published if still unpublished. */
  private void publishAndMark(OutboxEvent event) {
    OutboxEvent fresh = outboxEventRepository.findByIdWithProduct(event.getId()).orElse(null);
    if (fresh == null || fresh.getPublishedAt() != null) {
      return;
    }

    NotificationMessage message = new NotificationMessage(
        fresh.getId(),
        fresh.getProduct().getId(),
        fresh.getEventType(),
        fresh.getPayload());

    rabbitTemplate.convertAndSend(EXCHANGE, NOTIFICATION_SEND_QUEUE, message);
    fresh.setPublishedAt(Instant.now());
    outboxEventRepository.save(fresh);
  }
}
