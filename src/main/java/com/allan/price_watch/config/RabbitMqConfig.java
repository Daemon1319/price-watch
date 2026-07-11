package com.allan.price_watch.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * Two queues, both quorum type (RabbitMQ 4.x's modern durable-queue
 * default — classic queue mirroring was removed entirely in 4.0, quorum
 * queues are the replacement even on a single node like this project's
 * {@code compose.yaml} setup) and each backed by its own dead-letter
 * queue. A message lands in its DLQ when {@code ScrapeWorker} or
 * {@code NotificationWorker} rejects it (via manual NACK, per plan §5) —
 * this config only declares the topology, the retry/rejection logic
 * itself lives in the listener classes.
 *
 * <p>{@code JacksonJsonMessageConverter} (Spring AMQP 4.0+, Jackson 3) is
 * declared once here; Spring Boot auto-wires it into both the
 * auto-configured {@code RabbitTemplate} (for publishing) and the
 * {@code @RabbitListener} container factory (for consuming) automatically
 * — no separate container factory bean needed. The deprecated
 * {@code Jackson2JsonMessageConverter} is avoided on purpose; it's marked
 * for removal as of Spring AMQP 4.0.
 */
@Configuration
public class RabbitMqConfig {

  public static final String EXCHANGE = "price-watch.exchange";
  public static final String DEAD_LETTER_EXCHANGE = "price-watch.dlx";

  public static final String PRODUCT_CHECK_QUEUE = "product.check";
  public static final String PRODUCT_CHECK_DLQ = "product.check.dlq";

  public static final String NOTIFICATION_SEND_QUEUE = "notification.send";
  public static final String NOTIFICATION_SEND_DLQ = "notification.send.dlq";

  @Bean
  public JacksonJsonMessageConverter messageConverter(JsonMapper jsonMapper) {
    return new JacksonJsonMessageConverter(jsonMapper);
  }

  @Bean
  public DirectExchange exchange() {
    return new DirectExchange(EXCHANGE);
  }

  @Bean
  public DirectExchange deadLetterExchange() {
    return new DirectExchange(DEAD_LETTER_EXCHANGE);
  }

  /**
   * Quorum delivery-limit: after N failed deliveries the broker dead-letters
   * the message (instead of infinite requeue loops from NotificationWorker's
   * NACK-requeue). Product-check failures already NACK without requeue, but
   * the limit still caps accidental requeue=true paths.
   *
   * <p>If you change these arguments after the queues already exist, RabbitMQ
   * will refuse redeclaration — delete the queues (or wipe the rabbitmq
   * volume) once so the new topology is applied.
   */
  @Bean
  public Queue productCheckQueue() {
    return QueueBuilder.durable(PRODUCT_CHECK_QUEUE)
        .quorum()
        .deadLetterExchange(DEAD_LETTER_EXCHANGE)
        .deadLetterRoutingKey(PRODUCT_CHECK_DLQ)
        .withArgument("x-delivery-limit", 3)
        .build();
  }

  @Bean
  public Queue productCheckDlq() {
    return QueueBuilder.durable(PRODUCT_CHECK_DLQ).quorum().build();
  }

  @Bean
  public Queue notificationSendQueue() {
    return QueueBuilder.durable(NOTIFICATION_SEND_QUEUE)
        .quorum()
        .deadLetterExchange(DEAD_LETTER_EXCHANGE)
        .deadLetterRoutingKey(NOTIFICATION_SEND_DLQ)
        .withArgument("x-delivery-limit", 3)
        .build();
  }

  @Bean
  public Queue notificationSendDlq() {
    return QueueBuilder.durable(NOTIFICATION_SEND_DLQ).quorum().build();
  }

  @Bean
  public Binding productCheckBinding(Queue productCheckQueue, DirectExchange exchange) {
    return BindingBuilder.bind(productCheckQueue).to(exchange).with(PRODUCT_CHECK_QUEUE);
  }

  @Bean
  public Binding notificationSendBinding(Queue notificationSendQueue, DirectExchange exchange) {
    return BindingBuilder.bind(notificationSendQueue).to(exchange).with(NOTIFICATION_SEND_QUEUE);
  }

  @Bean
  public Binding productCheckDlqBinding(Queue productCheckDlq, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(productCheckDlq).to(deadLetterExchange).with(PRODUCT_CHECK_DLQ);
  }

  @Bean
  public Binding notificationSendDlqBinding(Queue notificationSendDlq, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(notificationSendDlq).to(deadLetterExchange).with(NOTIFICATION_SEND_DLQ);
  }
}