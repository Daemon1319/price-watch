package com.allan.price_watch.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/** Issues and validates short-lived JWT access tokens (not refresh tokens). */
@Component
public class JwtService {

  private final SecretKey key;
  private final Duration accessTokenTtl;

  public JwtService(
      @Value("${app.jwt.secret}") String base64Secret,
      @Value("${app.jwt.access-token-ttl:15m}") Duration accessTokenTtl) {
    this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    this.accessTokenTtl = accessTokenTtl;
  }

  /** Builds a signed access JWT for the given user. */
  public String generateAccessToken(UUID userId, String email) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId.toString())
        .claim("email", email)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(accessTokenTtl)))
        .signWith(key)
        .compact();
  }

  /** Reads the user id from a valid access token's subject claim. */
  public UUID extractUserId(String token) {
    return UUID.fromString(parseClaims(token).getSubject());
  }

  /** Reads the email claim from a valid access token. */
  public String extractEmail(String token) {
    return parseClaims(token).get("email", String.class);
  }

  /** Returns true if the token's signature and expiry are valid. */
  public boolean isTokenValid(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  /** Access-token lifetime in seconds (for API responses). */
  public long getAccessTokenTtlSeconds() {
    return accessTokenTtl.getSeconds();
  }
}
