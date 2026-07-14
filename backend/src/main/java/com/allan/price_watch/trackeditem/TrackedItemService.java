package com.allan.price_watch.trackeditem;

import java.util.Collection;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.allan.price_watch.auth.repository.UserRepository;
import com.allan.price_watch.common.exception.DuplicateTrackingException;
import com.allan.price_watch.common.exception.TrackedItemNotFoundException;
import com.allan.price_watch.common.exception.TrackingLimitExceededException;
import com.allan.price_watch.product.ProductHealth;
import com.allan.price_watch.product.ProductService;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.trackeditem.dto.CreateTrackedItemRequest;
import com.allan.price_watch.trackeditem.dto.TrackedItemResponse;
import com.allan.price_watch.trackeditem.dto.UpdateTrackedItemRequest;
import com.allan.price_watch.trackeditem.entity.TrackedItem;
import com.allan.price_watch.trackeditem.entity.TrackedItemStatus;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

/** Owns create/list/update/delete of a user's product subscriptions. */
@Service
public class TrackedItemService {

  private final TrackedItemRepository trackedItemRepository;
  private final UserRepository userRepository;
  private final ProductService productService;
  private final TransactionTemplate transactionTemplate;
  private final int maxItemsPerUser;

  public TrackedItemService(
      TrackedItemRepository trackedItemRepository,
      UserRepository userRepository,
      ProductService productService,
      TransactionTemplate transactionTemplate,
      @Value("${app.tracking.max-items-per-user:50}") int maxItemsPerUser) {
    this.trackedItemRepository = trackedItemRepository;
    this.userRepository = userRepository;
    this.productService = productService;
    this.transactionTemplate = transactionTemplate;
    this.maxItemsPerUser = maxItemsPerUser;
  }

  /** Tracks a URL for the user (scrape first, then insert the subscription). */
  @CacheEvict(value = "dashboardSummary", key = "#userId")
  public TrackedItemResponse create(UUID userId, CreateTrackedItemRequest request) {
    // Keep the long scrape outside a DB transaction. Uniqlo requires color+size.
    Product product = productService.findOrCreateByUrl(
        request.url(), request.colorCode(), request.sizeCode());

    return transactionTemplate.execute(status -> insertTrackedItem(userId, product, request));
  }

  /** Inserts a tracked item with limit and duplicate checks. */
  private TrackedItemResponse insertTrackedItem(
      UUID userId, Product product, CreateTrackedItemRequest request) {
    if (trackedItemRepository.existsByUserIdAndProductId(userId, product.getId())) {
      throw new DuplicateTrackingException();
    }

    long currentCount = trackedItemRepository.countByUserId(userId);
    if (currentCount >= maxItemsPerUser) {
      throw new TrackingLimitExceededException(maxItemsPerUser);
    }

    TrackedItem trackedItem = TrackedItem.builder()
        .user(userRepository.getReferenceById(userId))
        .product(product)
        .priceThreshold(request.priceThreshold())
        .notifyOnRestockOnly(request.notifyOnRestockOnly())
        .status(TrackedItemStatus.ACTIVE)
        .build();

    try {
      trackedItem = trackedItemRepository.saveAndFlush(trackedItem);
    } catch (DataIntegrityViolationException e) {
      // Concurrent create for the same user+product.
      throw new DuplicateTrackingException();
    }

    return TrackedItemResponse.from(trackedItem, product);
  }

  /**
   * Lists the user's tracked items.
   *
   * @param unhealthyOnly when true, only items whose product is unhealthy (ignores status filter)
   * @param statuses optional ACTIVE/PAUSED filter when not unhealthy-only
   */
  @Transactional(readOnly = true)
  public Page<TrackedItemResponse> list(
      UUID userId,
      Collection<TrackedItemStatus> statuses,
      boolean unhealthyOnly,
      Pageable pageable) {
    Page<TrackedItem> page;
    if (unhealthyOnly) {
      page = trackedItemRepository.findByUserIdAndProduct_ConsecutiveFailuresGreaterThanEqual(
          userId, ProductHealth.UNHEALTHY_FAILURE_THRESHOLD, pageable);
    } else if (statuses == null || statuses.isEmpty()) {
      page = trackedItemRepository.findByUserId(userId, pageable);
    } else {
      page = trackedItemRepository.findByUserIdAndStatusIn(userId, statuses, pageable);
    }

    return page.map(TrackedItemResponse::from);
  }

  /** Returns one tracked item owned by the user. */
  @Transactional(readOnly = true)
  public TrackedItemResponse get(UUID userId, UUID id) {
    return TrackedItemResponse.from(findOwned(userId, id));
  }

  /** Patches threshold, restock-only flag, and/or status for a tracked item. */
  @Transactional
  @CacheEvict(value = "dashboardSummary", key = "#userId")
  public TrackedItemResponse update(UUID userId, UUID id, UpdateTrackedItemRequest request) {
    TrackedItem trackedItem = findOwned(userId, id);

    if (request.priceThreshold() != null) {
      trackedItem.setPriceThreshold(request.priceThreshold());
    }
    if (request.notifyOnRestockOnly() != null) {
      trackedItem.setNotifyOnRestockOnly(request.notifyOnRestockOnly());
    }
    if (request.status() != null) {
      trackedItem.setStatus(request.status());
    }

    return TrackedItemResponse.from(trackedItem);
  }

  /** Deletes a tracked item owned by the user. */
  @Transactional
  @CacheEvict(value = "dashboardSummary", key = "#userId")
  public void delete(UUID userId, UUID id) {
    trackedItemRepository.delete(findOwned(userId, id));
  }

  /** Loads a tracked item only if it belongs to the given user. */
  private TrackedItem findOwned(UUID userId, UUID id) {
    return trackedItemRepository.findByIdAndUserId(id, userId)
        .orElseThrow(TrackedItemNotFoundException::new);
  }
}
