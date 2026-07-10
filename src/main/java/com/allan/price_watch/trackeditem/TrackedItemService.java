package com.allan.price_watch.trackeditem;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allan.price_watch.auth.repository.UserRepository;
import com.allan.price_watch.common.exception.DuplicateTrackingException;
import com.allan.price_watch.common.exception.TrackedItemNotFoundException;
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
 */
@Service
public class TrackedItemService {

  private final TrackedItemRepository trackedItemRepository;
  private final UserRepository userRepository;
  private final ProductService productService;

  public TrackedItemService(
      TrackedItemRepository trackedItemRepository,
      UserRepository userRepository,
      ProductService productService) {
    this.trackedItemRepository = trackedItemRepository;
    this.userRepository = userRepository;
    this.productService = productService;
  }

  @Transactional
  public TrackedItemResponse create(UUID userId, CreateTrackedItemRequest request) {
    Product product = productService.findOrCreateByUrl(request.url());

    if (trackedItemRepository.existsByUserIdAndProductId(userId, product.getId())) {
      throw new DuplicateTrackingException();
    }

    TrackedItem trackedItem = TrackedItem.builder()
        // getReferenceById gives Hibernate a lazy proxy for the FK column
        // without an extra SELECT — userId already came from a verified
        // JWT, so there's nothing else about the User worth fetching here.
        .user(userRepository.getReferenceById(userId))
        .product(product)
        .priceThreshold(request.priceThreshold())
        .notifyOnRestockOnly(request.notifyOnRestockOnly())
        .status(TrackedItemStatus.ACTIVE)
        .build();

    trackedItem = trackedItemRepository.save(trackedItem);

    return TrackedItemResponse.from(trackedItem);
  }

  public Page<TrackedItemResponse> list(UUID userId, Collection<TrackedItemStatus> statuses, Pageable pageable) {
    Page<TrackedItem> page = (statuses == null || statuses.isEmpty())
        ? trackedItemRepository.findByUserId(userId, pageable)
        : trackedItemRepository.findByUserIdAndStatusIn(userId, statuses, pageable);

    return page.map(TrackedItemResponse::from);
  }

  public TrackedItemResponse get(UUID userId, UUID id) {
    return TrackedItemResponse.from(findOwned(userId, id));
  }

  @Transactional
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

    // No explicit save() — trackedItem is managed within this
    // @Transactional method, so Hibernate's dirty checking flushes these
    // field changes automatically at commit.
    return TrackedItemResponse.from(trackedItem);
  }

  @Transactional
  public void delete(UUID userId, UUID id) {
    trackedItemRepository.delete(findOwned(userId, id));
  }

  private TrackedItem findOwned(UUID userId, UUID id) {
    return trackedItemRepository.findByIdAndUserId(id, userId)
        .orElseThrow(TrackedItemNotFoundException::new);
  }
}