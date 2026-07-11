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
import com.allan.price_watch.product.ProductService;
import com.allan.price_watch.product.entity.Product;
import com.allan.price_watch.trackeditem.dto.CreateTrackedItemRequest;
import com.allan.price_watch.trackeditem.dto.TrackedItemResponse;
import com.allan.price_watch.trackeditem.dto.UpdateTrackedItemRequest;
import com.allan.price_watch.trackeditem.entity.TrackedItem;
import com.allan.price_watch.trackeditem.entity.TrackedItemStatus;
import com.allan.price_watch.trackeditem.repository.TrackedItemRepository;

/**
 * Owns the per-user subscription lifecycle. Product lookup/creation is
 * deliberately delegated to {@link ProductService} — this class shouldn't
 * need to know how URL normalization, site detection, or the initial
 * scrape work, only that "give me a Product for this URL, creating it if
 * needed" is a single call away.
 *
 * <p>{@code create} intentionally does <strong>not</strong> wrap the HTTP
 * scrape in a DB transaction: {@code findOrCreateByUrl} may take seconds
 * on Uniqlo. Only the short tracked-item insert runs in a TX so we don't
 * hold a JDBC connection open during network I/O or poison the outer TX
 * when a concurrent first-track races on {@code products.normalized_url}.
 *
 * <p>Mutations evict {@code dashboardSummary} for the user so the dashboard
 * count does not stay stale for the Redis cache TTL (scrapes already evict
 * via {@code ScrapeResultService}).
 */
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

  @CacheEvict(value = "dashboardSummary", key = "#userId")
  public TrackedItemResponse create(UUID userId, CreateTrackedItemRequest request) {
    // Scrape / product dedup outside any TX (may take seconds).
    Product product = productService.findOrCreateByUrl(request.url());

    return transactionTemplate.execute(status -> insertTrackedItem(userId, product, request));
  }

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
      // Concurrent POST for the same user+product lost the race on the unique index.
      throw new DuplicateTrackingException();
    }

    return TrackedItemResponse.from(trackedItem, product);
  }

  @Transactional(readOnly = true)
  public Page<TrackedItemResponse> list(UUID userId, Collection<TrackedItemStatus> statuses, Pageable pageable) {
    Page<TrackedItem> page = (statuses == null || statuses.isEmpty())
        ? trackedItemRepository.findByUserId(userId, pageable)
        : trackedItemRepository.findByUserIdAndStatusIn(userId, statuses, pageable);

    return page.map(TrackedItemResponse::from);
  }

  @Transactional(readOnly = true)
  public TrackedItemResponse get(UUID userId, UUID id) {
    return TrackedItemResponse.from(findOwned(userId, id));
  }

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

  @Transactional
  @CacheEvict(value = "dashboardSummary", key = "#userId")
  public void delete(UUID userId, UUID id) {
    trackedItemRepository.delete(findOwned(userId, id));
  }

  private TrackedItem findOwned(UUID userId, UUID id) {
    return trackedItemRepository.findByIdAndUserId(id, userId)
        .orElseThrow(TrackedItemNotFoundException::new);
  }
}
