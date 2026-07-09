package com.allan.price_watch.trackeditem.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.allan.price_watch.trackeditem.entity.PriceHistory;

/**
 * Both methods sort newest-first and are backed by the
 * {@code price_history_product_id_recorded_at_idx} composite index from
 * {@code V2__price_history.sql} — {@code (product_id, recorded_at DESC)}
 * matches this access pattern exactly, so neither query needs a sort step.
 */
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, UUID> {

  Page<PriceHistory> findByProductIdOrderByRecordedAtDesc(UUID productId, Pageable pageable);

  Page<PriceHistory> findByProductIdAndRecordedAtBetweenOrderByRecordedAtDesc(
      UUID productId, Instant from, Instant to, Pageable pageable);
}