package com.allan.price_watch.notification.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.allan.price_watch.notification.entity.OutboxEvent;

/**
 * {@code findByPublishedAtIsNullOrderByCreatedAtAsc} is the exact query
 * {@code OutboxRelay} polls every 5-10s (plan §5, step 4) — oldest
 * unpublished events first, batch size controlled by the {@code Pageable}
 * the caller passes in (e.g. {@code PageRequest.of(0, 50)}) rather than
 * hardcoded here. Matches the partial index
 * {@code outbox_events_unpublished_idx} from {@code V3__outbox_events.sql},
 * so this stays fast regardless of how many published rows accumulate.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}