package com.allan.price_watch.notification;

/** Shared payload keys for outbox event JSON. */
public final class OutboxPayloadKeys {

  public static final String OLD_PRICE = "oldPrice";
  public static final String NEW_PRICE = "newPrice";

  private OutboxPayloadKeys() {
  }
}
