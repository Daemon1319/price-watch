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

/**
 * Issues and validates <b>access tokens only</b>. Refresh tokens (see
 * {@code RefreshToken}) are deliberately <em>not</em> JWTs — they're opaque
 * random strings whose hash is stored server-side, which is what makes them
 * instantly revocable on logout. A JWT can't be un-issued before it expires
 * without a server-side blocklist, which is exactly the statelessness this
 * class exists to avoid; keeping the access token's lifetime short (15 min,
 * see {@code app.jwt.access-token-ttl}) is what makes that an acceptable
 * tradeoff instead of a security hole.
 *
 * <p>Uses JJWT 0.13.0's current builder/parser API
 * ({@code Jwts.builder()...signWith(key)}, {@code Jwts.parser()
 * .verifyWith(key)...parseSignedClaims(token)}) — the older
 * {@code setSubject}/{@code parserBuilder()} methods this replaced are
 * deprecated as of 0.12.0.
 */
@Component
public class JwtService {

  private final SecretKey key;
  private final Duration accessTokenTtl;

  public JwtService(
      @Value("${app.jwt.secret}") String base64Secret,
      @Value("${app.jwt.access-token-ttl:15m}") Duration accessTokenTtl) {
    // The configured secret must be Base64-encoded and decode to at least
    // 32 bytes (256 bits) — HS256's minimum key length per RFC 7518 §3.2.
    // Generate one with: openssl rand -base64 32
    this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    this.accessTokenTtl = accessTokenTtl;
  }

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

  public UUID extractUserId(String token) {
    return UUID.fromString(parseClaims(token).getSubject());
  }

  public String extractEmail(String token) {
    return parseClaims(token).get("email", String.class);
  }

  /**
   * Cheap to call before every extraction — {@code parseSignedClaims}
   * already verifies the signature and expiration, so a caught exception
   * here reliably means "reject this request," not just "malformed."
   */
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

  public long getAccessTokenTtlSeconds() {
    return accessTokenTtl.getSeconds();
  }
}