package com.allan.price_watch.notification;

/**
 * Shared JSON keys for {@code outbox_events.payload}. Writers
 * ({@code ScrapeResultService}) and readers ({@code DashboardService},
 * {@code NotificationWorker}) must use the same names.
 */
public final class OutboxPayloadKeys {

  public static final String OLD_PRICE = "oldPrice";
  public static final String NEW_PRICE = "newPrice";

  private OutboxPayloadKeys() {
  }
}
