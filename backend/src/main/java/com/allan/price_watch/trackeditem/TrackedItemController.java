package com.allan.price_watch.trackeditem;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.allan.price_watch.common.web.PageResponse;
import com.allan.price_watch.trackeditem.dto.CreateTrackedItemRequest;
import com.allan.price_watch.trackeditem.dto.TrackedItemResponse;
import com.allan.price_watch.trackeditem.dto.UpdateTrackedItemRequest;
import com.allan.price_watch.trackeditem.entity.TrackedItemStatus;

import jakarta.validation.Valid;

/** REST API for a user's tracked-item subscriptions. */
@RestController
@RequestMapping("/api/v1/tracked-items")
public class TrackedItemController {

  private final TrackedItemService trackedItemService;

  public TrackedItemController(TrackedItemService trackedItemService) {
    this.trackedItemService = trackedItemService;
  }

  /** Starts tracking a product URL for the authenticated user. */
  @PostMapping
  public ResponseEntity<TrackedItemResponse> create(
      @AuthenticationPrincipal UUID userId,
      @Valid @RequestBody CreateTrackedItemRequest request) {
    TrackedItemResponse response = trackedItemService.create(userId, request);
    return ResponseEntity
        .created(URI.create("/api/v1/tracked-items/" + response.id()))
        .body(response);
  }

  /** Lists the user's tracked items with optional status filter. */
  @GetMapping
  public PageResponse<TrackedItemResponse> list(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(required = false) List<String> status,
      @PageableDefault(size = 20) Pageable pageable) {
    return PageResponse.from(trackedItemService.list(userId, parseStatuses(status), pageable));
  }

  /** Returns a single tracked item owned by the user. */
  @GetMapping("/{id}")
  public TrackedItemResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    return trackedItemService.get(userId, id);
  }

  /** Updates notification preferences or status for a tracked item. */
  @PatchMapping("/{id}")
  public TrackedItemResponse update(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateTrackedItemRequest request) {
    return trackedItemService.update(userId, id, request);
  }

  /** Stops tracking an item for the user. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    trackedItemService.delete(userId, id);
    return ResponseEntity.noContent().build();
  }

  /** Parses status query params into enum values. */
  private List<TrackedItemStatus> parseStatuses(List<String> rawStatuses) {
    if (rawStatuses == null || rawStatuses.isEmpty()) {
      return List.of();
    }
    try {
      return rawStatuses.stream().map(s -> TrackedItemStatus.valueOf(s.toUpperCase())).toList();
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value. Expected ACTIVE or PAUSED.");
    }
  }
}
