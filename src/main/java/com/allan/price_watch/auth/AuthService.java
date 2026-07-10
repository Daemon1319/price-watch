package com.allan.price_watch.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allan.price_watch.auth.dto.LoginRequest;
import com.allan.price_watch.auth.dto.LoginResponse;
import com.allan.price_watch.auth.dto.RefreshRequest;
import com.allan.price_watch.auth.dto.RegisterRequest;
import com.allan.price_watch.auth.entity.RefreshToken;
import com.allan.price_watch.auth.entity.User;
import com.allan.price_watch.auth.repository.RefreshTokenRepository;
import com.allan.price_watch.auth.repository.UserRepository;
import com.allan.price_watch.common.exception.EmailAlreadyRegisteredException;
import com.allan.price_watch.common.exception.InvalidCredentialsException;
import com.allan.price_watch.common.exception.InvalidRefreshTokenException;
import com.allan.price_watch.security.JwtService;

/**
 * Refresh tokens issued here are opaque random strings, not JWTs — see the
 * note on {@code RefreshToken}/{@code JwtService} for why. This class is
 * also where refresh token <b>rotation</b> happens: every successful
 * {@code /auth/refresh} call revokes the token it was given and issues a
 * brand new pair, rather than reusing the same refresh token until it
 * naturally expires. That's the current OWASP-recommended pattern for
 * refresh tokens — it means a leaked-but-already-used refresh token is
 * worthless to whoever leaked it.
 */
@Service
public class AuthService {

  private static final long REFRESH_TOKEN_TTL_DAYS = 7;

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Transactional
  public LoginResponse register(RegisterRequest request) {
    String email = request.email().toLowerCase();

    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new EmailAlreadyRegisteredException();
    }

    User user = User.builder()
        .email(email)
        .passwordHash(passwordEncoder.encode(request.password()))
        .build();

    user = userRepository.save(user);

    return issueTokenPair(user);
  }

  @Transactional
  public LoginResponse login(LoginRequest request) {
    String email = request.email().toLowerCase();

    // Same exception whether the email doesn't exist or the password is
    // wrong — telling those two apart in the response would let an
    // attacker enumerate registered emails one guess at a time.
    User user = userRepository.findByEmailIgnoreCase(email)
        .orElseThrow(InvalidCredentialsException::new);

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }

    return issueTokenPair(user);
  }

  @Transactional
  public LoginResponse refresh(RefreshRequest request) {
    RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
        .filter(rt -> rt.getRevokedAt() == null)
        .filter(rt -> rt.getExpiresAt().isAfter(Instant.now()))
        .orElseThrow(InvalidRefreshTokenException::new);

    existing.setRevokedAt(Instant.now());
    refreshTokenRepository.save(existing);

    return issueTokenPair(existing.getUser());
  }

  @Transactional
  public void logout(UUID userId, RefreshRequest request) {
    // Scoped to the calling user, not just the token hash — stops one
    // user from revoking a token that happens to belong to someone else
    // (defense in depth; a correct client would never send another
    // user's token, but the check costs nothing to add).
    refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
        .filter(rt -> rt.getUser().getId().equals(userId))
        .ifPresent(rt -> {
          rt.setRevokedAt(Instant.now());
          refreshTokenRepository.save(rt);
        });
    // No exception if the token is already gone/invalid — logout is
    // idempotent from the client's point of view either way.
  }

  private LoginResponse issueTokenPair(User user) {
    String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());

    String rawRefreshToken = generateOpaqueToken();
    RefreshToken refreshToken = RefreshToken.builder()
        .user(user)
        .tokenHash(hash(rawRefreshToken))
        .expiresAt(Instant.now().plus(Duration.ofDays(REFRESH_TOKEN_TTL_DAYS)))
        .build();
    refreshTokenRepository.save(refreshToken);

    return new LoginResponse(accessToken, rawRefreshToken, jwtService.getAccessTokenTtlSeconds());
  }

  /** 32 random bytes, URL-safe Base64 — not a JWT, not decodable as one. */
  private String generateOpaqueToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * SHA-256, deliberately not bcrypt/Argon2 — refresh tokens are already
   * 256 bits of {@code SecureRandom} entropy, not user-chosen secrets, so
   * there's no brute-forceable keyspace to slow an attacker down against.
   * A fast cryptographic hash is the right tool here; an adaptive one
   * would just waste CPU on every refresh/logout call for no security
   * benefit.
   */
  private String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed available on every JVM per the Java
      // Cryptography Architecture spec — this branch is unreachable.
      throw new IllegalStateException(e);
    }
  }
}