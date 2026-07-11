package com.allan.price_watch.trackeditem.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.allan.price_watch.trackeditem.entity.PriceHistory;

/** Price history queries, newest first. */
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, UUID> {

  Page<PriceHistory> findByProductIdOrderByRecordedAtDesc(UUID productId, Pageable pageable);

  Page<PriceHistory> findByProductIdAndRecordedAtBetweenOrderByRecordedAtDesc(
      UUID productId, Instant from, Instant to, Pageable pageable);
}
