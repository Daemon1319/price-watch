package com.allan.price_watch.product.dto;

/** Result of queuing scrapes for every ACTIVE product the user tracks. */
public record ManualCheckAllResponse(int queued) {
}
