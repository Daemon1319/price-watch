package com.allan.price_watch.notification;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.allan.price_watch.notification.dto.NotificationMessage;
import com.allan.price_watch.notification.entity.OutboxEventType;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.product.repository.ProductRepository;
import com.allan.price_watch.trackeditem.entity.TrackedItem;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;
import com.rabbitmq.client.Channel;

import io.micrometer.core.instrument.MeterRegistry;

import static com.allan.price_watch.config.RabbitMqConfig.NOTIFICATION_SEND_QUEUE;

/**
 * Consumes {@code notification.send} messages published by
 * {@code OutboxRelay}. Fans out to every ACTIVE tracker for the product,
 * applies per-user preferences, and sends email. Uses manual ACK — only
 * ACKs after all eligible emails succeed so a transient SMTP failure
 * requeues the message (RabbitMQ redelivery / eventual DLQ after
 * retries, depending on broker policy).
 */
@Component
public class NotificationWorker {

  private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);

  private final ProductRepository productRepository;
  private final TrackedItemRepository trackedItemRepository;
  private final NotificationMailService mailService;
  private final MeterRegistry meterRegistry;

  public NotificationWorker(
      ProductRepository productRepository,
      TrackedItemRepository trackedItemRepository,
      NotificationMailService mailService,
      MeterRegistry meterRegistry) {
    this.productRepository = productRepository;
    this.trackedItemRepository = trackedItemRepository;
    this.mailService = mailService;
    this.meterRegistry = meterRegistry;
  }

  @RabbitListener(queues = NOTIFICATION_SEND_QUEUE, ackMode = "MANUAL")
  public void handle(
      NotificationMessage message,
      Channel channel,
      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

    try {
      deliver(message);
      channel.basicAck(deliveryTag, false);
      meterRegistry.counter("notification.sent", "event_type", message.eventType().name()).increment();
    } catch (Exception e) {
      log.warn("Notification delivery failed for outbox event {}: {}",
          message.outboxEventId(), e.getMessage());
      meterRegistry.counter("notification.failed", "event_type", message.eventType().name()).increment();
      // requeue=true for transient SMTP blips. Quorum x-delivery-limit (see
      // RabbitMqConfig) dead-letters after a few attempts so poison messages
      // cannot loop forever.
      channel.basicNack(deliveryTag, false, true);
    }
  }

  private void deliver(NotificationMessage message) {
    Product product = productRepository.findById(message.productId()).orElse(null);
    if (product == null) {
      log.debug("Product {} gone; dropping notification {}", message.productId(), message.outboxEventId());
      return;
    }

    List<TrackedItem> trackers = trackedItemRepository.findActiveByProductIdWithUser(message.productId());
    for (TrackedItem tracker : trackers) {
      if (!shouldNotify(tracker, message.eventType(), message.payload())) {
        continue;
      }
      mailService.send(tracker.getUser().getEmail(), product, message.eventType(), message.payload());
    }
  }

  /**
   * Preference rules:
   * <ul>
   *   <li>{@code notifyOnRestockOnly=true} → only RESTOCK events</li>
   *   <li>{@code notifyOnRestockOnly=false} → PRICE_DROP (threshold) + RESTOCK</li>
   *   <li>{@code priceThreshold} null → any drop; non-null → drop amount must be ≥ threshold</li>
   * </ul>
   */
  static boolean shouldNotify(TrackedItem tracker, OutboxEventType eventType, Map<String, Object> payload) {
    if (tracker.isNotifyOnRestockOnly()) {
      return eventType == OutboxEventType.RESTOCK;
    }

    return switch (eventType) {
      case RESTOCK -> true;
      case PRICE_DROP -> meetsPriceDropThreshold(tracker.getPriceThreshold(), payload);
      case PRICE_INCREASE, OUT_OF_STOCK -> false;
    };
  }

  private static boolean meetsPriceDropThreshold(BigDecimal threshold, Map<String, Object> payload) {
    if (threshold == null) {
      return true;
    }
    BigDecimal oldPrice = toBigDecimal(payload.get(OutboxPayloadKeys.OLD_PRICE));
    BigDecimal newPrice = toBigDecimal(payload.get(OutboxPayloadKeys.NEW_PRICE));
    if (oldPrice == null || newPrice == null) {
      return true;
    }
    BigDecimal drop = oldPrice.subtract(newPrice);
    return drop.compareTo(threshold) >= 0;
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    return new BigDecimal(value.toString());
  }
}
