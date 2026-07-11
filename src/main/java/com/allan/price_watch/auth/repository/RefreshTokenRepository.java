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

/**
 * Lookups are always by {@code tokenHash}, never by {@code id} — the refresh
 * flow and logout both start from the raw token the client sent, which the
 * caller hashes before reaching this repository. See the note on
 * {@code RefreshToken.tokenHash} for why the raw token is never stored.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Exclusive row lock for refresh rotation so two concurrent refresh calls
   * with the same token cannot both mint a new pair.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  /**
   * Steal / reuse detection: revoke every still-active refresh token for a
   * user when a previously rotated token is presented again.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update RefreshToken rt
      set rt.revokedAt = :now
      where rt.user.id = :userId
        and rt.revokedAt is null
      """)
  int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}