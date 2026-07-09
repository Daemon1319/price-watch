package com.allan.price_watch.auth.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single refresh token session for a user. {@code tokenHash} stores a hash
 * (e.g. SHA-256) of the actual refresh token — never the raw token itself —
 * so a database leak alone can't be used to forge sessions. Lookups during
 * the refresh flow happen by hash, not by id.
 *
 * <p>{@code revokedAt} is set by the application on logout (see
 * {@code AuthService.logout}), unlike {@code id}/{@code createdAt} which are
 * database-generated — hence only those two carry {@code @Generated}.
 *
 * <p>No cleanup of expired/revoked rows happens here; that's intentionally
 * left to a future scheduled job (plan §16) rather than handled per-request.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

  @Id
  @Generated(event = EventType.INSERT)
  @Column(name = "id", insertable = false, updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
  private Instant createdAt;
}