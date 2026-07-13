package com.allan.price_watch.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.allan.price_watch.auth.entity.RefreshToken;

import jakarta.persistence.LockModeType;

/** Lookup and revoke helpers for hashed refresh tokens. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /** Finds a refresh token row by its stored hash. */
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** Same as findByTokenHash, with a pessimistic write lock for rotation. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  /** Revokes every still-active refresh token for a user (reuse detection). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update RefreshToken rt
      set rt.revokedAt = :now
      where rt.user.id = :userId
        and rt.revokedAt is null
      """)
  int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
