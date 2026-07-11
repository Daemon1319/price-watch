package com.allan.price_watch.notification;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.allan.price_watch.notification.entity.OutboxEventType;
import com.allan.price_watch.product.entity.Product;

/** Builds and sends price-drop / restock notification emails. */
@Service
public class NotificationMailService {

  private static final Logger log = LoggerFactory.getLogger(NotificationMailService.class);

  private final JavaMailSender mailSender;
  private final String fromAddress;

  public NotificationMailService(
      JavaMailSender mailSender,
      @Value("${app.mail.from:pricewatch@localhost}") String fromAddress) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
  }

  /** Sends one notification email for the product event. */
  public void send(String toEmail, Product product, OutboxEventType eventType, Map<String, Object> payload) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(toEmail);
    message.setSubject(buildSubject(product, eventType));
    message.setText(buildBody(product, eventType, payload));

    mailSender.send(message);
    log.info("Sent {} notification for product {} to {}", eventType, product.getId(), toEmail);
  }

  private String buildSubject(Product product, OutboxEventType eventType) {
    String name = displayName(product);
    return switch (eventType) {
      case PRICE_DROP -> "[PriceWatch] Price drop: " + name;
      case RESTOCK -> "[PriceWatch] Back in stock: " + name;
      case PRICE_INCREASE -> "[PriceWatch] Price increase: " + name;
      case OUT_OF_STOCK -> "[PriceWatch] Out of stock: " + name;
    };
  }

  private String buildBody(Product product, OutboxEventType eventType, Map<String, Object> payload) {
    String name = displayName(product);
    String url = product.getNormalizedUrl();
    String variantLine = variantLine(product);

    return switch (eventType) {
      case PRICE_DROP -> {
        BigDecimal oldPrice = toBigDecimal(payload.get("oldPrice"));
        BigDecimal newPrice = toBigDecimal(payload.get("newPrice"));
        yield name + " dropped from ₱" + oldPrice + " to ₱" + newPrice + "."
            + variantLine + "\n\n" + url;
      }
      case RESTOCK -> name + " is back in stock." + variantLine + "\n\n" + url;
      case PRICE_INCREASE -> name + " increased in price." + variantLine + "\n\n" + url;
      case OUT_OF_STOCK -> name + " is now out of stock." + variantLine + "\n\n" + url;
    };
  }

  /** Product name plus human-readable color/size when known. */
  private static String displayName(Product product) {
    String name = product.getName() != null ? product.getName() : product.getNormalizedUrl();
    String color = product.getColorName() != null ? product.getColorName() : product.getColorCode();
    String size = product.getSizeName() != null ? product.getSizeName() : product.getSizeCode();
    if (color == null && size == null) {
      return name;
    }
    if (color != null && size != null) {
      return name + " (" + color + " / " + size + ")";
    }
    return name + " (" + (color != null ? color : size) + ")";
  }

  private static String variantLine(Product product) {
    String color = product.getColorName() != null ? product.getColorName() : product.getColorCode();
    String size = product.getSizeName() != null ? product.getSizeName() : product.getSizeCode();
    if (color == null && size == null) {
      return "";
    }
    StringBuilder line = new StringBuilder("\nVariant: ");
    if (color != null) {
      line.append(color);
      if (product.getColorCode() != null && product.getColorName() != null) {
        line.append(" [").append(product.getColorCode()).append(']');
      }
    }
    if (size != null) {
      if (color != null) {
        line.append(" · ");
      }
      line.append(size);
      if (product.getSizeCode() != null && product.getSizeName() != null) {
        line.append(" [").append(product.getSizeCode()).append(']');
      }
    }
    return line.toString();
  }

  private static BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    return new BigDecimal(value.toString());
  }
}
