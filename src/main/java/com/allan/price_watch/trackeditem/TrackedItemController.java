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

/**
 * Every method takes {@code @AuthenticationPrincipal UUID userId} and
 * passes it straight to {@code TrackedItemService} — the actual ownership
 * enforcement lives at the repository query level (see
 * {@code TrackedItemRepository}), this controller just plumbs the
 * authenticated user through, it doesn't re-check anything itself.
 */
@RestController
@RequestMapping("/api/v1/tracked-items")
public class TrackedItemController {

  private final TrackedItemService trackedItemService;

  public TrackedItemController(TrackedItemService trackedItemService) {
    this.trackedItemService = trackedItemService;
  }

  @PostMapping
  public ResponseEntity<TrackedItemResponse> create(
      @AuthenticationPrincipal UUID userId,
      @Valid @RequestBody CreateTrackedItemRequest request) {
    TrackedItemResponse response = trackedItemService.create(userId, request);
    return ResponseEntity
        .created(URI.create("/api/v1/tracked-items/" + response.id()))
        .body(response);
  }

  @GetMapping
  public PageResponse<TrackedItemResponse> list(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(required = false) List<String> status,
      @PageableDefault(size = 20) Pageable pageable) {
    return PageResponse.from(trackedItemService.list(userId, parseStatuses(status), pageable));
  }

  @GetMapping("/{id}")
  public TrackedItemResponse get(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    return trackedItemService.get(userId, id);
  }

  @PatchMapping("/{id}")
  public TrackedItemResponse update(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateTrackedItemRequest request) {
    return trackedItemService.update(userId, id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
    trackedItemService.delete(userId, id);
    return ResponseEntity.noContent().build();
  }

  /**
   * {@code ResponseStatusException} rather than a new {@code ApplicationException}
   * subclass here on purpose — this is a controller-local input parsing
   * concern ("you sent a status value that isn't a real enum constant"),
   * not a domain rule. Spring MVC's default handling already converts it
   * into an RFC 9457 {@code ProblemDetail} automatically, no entry in
   * {@code GlobalExceptionHandler} needed.
   */
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