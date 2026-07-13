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

/** Declares product-check and notification queues with dead-letter topology. */
@Configuration
public class RabbitMqConfig {

  public static final String EXCHANGE = "price-watch.exchange";
  public static final String DEAD_LETTER_EXCHANGE = "price-watch.dlx";

  public static final String PRODUCT_CHECK_QUEUE = "product.check";
  public static final String PRODUCT_CHECK_DLQ = "product.check.dlq";

  public static final String NOTIFICATION_SEND_QUEUE = "notification.send";
  public static final String NOTIFICATION_SEND_DLQ = "notification.send.dlq";

  /** JSON converter shared by publishers and @RabbitListener consumers. */
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

  /** Quorum product-check queue with delivery-limit and DLQ routing. */
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

  /** Quorum notification queue with delivery-limit and DLQ routing. */
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
