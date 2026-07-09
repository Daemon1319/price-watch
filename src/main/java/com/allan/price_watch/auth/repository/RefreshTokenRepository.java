package com.allan.price_watch.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.allan.price_watch.auth.entity.RefreshToken;

/**
 * Lookups are always by {@code tokenHash}, never by {@code id} — the refresh
 * flow and logout both start from the raw token the client sent, which the
 * caller hashes before reaching this repository. See the note on
 * {@code RefreshToken.tokenHash} for why the raw token is never stored.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);
}