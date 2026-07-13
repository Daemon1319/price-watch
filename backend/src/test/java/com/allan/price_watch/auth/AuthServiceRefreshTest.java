package com.allan.price_watch.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.allan.price_watch.auth.entity.RefreshToken;
import com.allan.price_watch.auth.entity.User;
import com.allan.price_watch.auth.repository.RefreshTokenRepository;
import com.allan.price_watch.auth.repository.UserRepository;
import com.allan.price_watch.common.exception.InvalidRefreshTokenException;
import com.allan.price_watch.security.JwtService;

class AuthServiceRefreshTest {

  private RefreshTokenRepository refreshTokenRepository;
  private JwtService jwtService;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    UserRepository userRepository = mock(UserRepository.class);
    refreshTokenRepository = mock(RefreshTokenRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    jwtService = mock(JwtService.class);
    authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService);
  }

  @Test
  void reuseOfRevokedRefreshTokenRevokesAllActiveSessions() {
    UUID userId = UUID.randomUUID();
    User user = User.builder().id(userId).email("a@example.com").passwordHash("x").build();

    RefreshToken revoked = RefreshToken.builder()
        .user(user)
        .tokenHash("ignored-by-test")
        .expiresAt(Instant.now().plusSeconds(3600))
        .revokedAt(Instant.now().minusSeconds(10))
        .build();

    when(refreshTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(revoked));

    assertThrows(InvalidRefreshTokenException.class,
        () -> authService.refresh("stolen-old-refresh-token"));

    verify(refreshTokenRepository).revokeAllActiveForUser(eq(userId), any(Instant.class));
    verify(jwtService, never()).generateAccessToken(any(), any());
  }

  @Test
  void expiredRefreshDoesNotTriggerFamilyRevoke() {
    UUID userId = UUID.randomUUID();
    User user = User.builder().id(userId).email("a@example.com").passwordHash("x").build();

    RefreshToken expired = RefreshToken.builder()
        .user(user)
        .tokenHash("ignored")
        .expiresAt(Instant.now().minusSeconds(60))
        .revokedAt(null)
        .build();

    when(refreshTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(expired));

    assertThrows(InvalidRefreshTokenException.class,
        () -> authService.refresh("expired-token"));

    verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
  }

  @Test
  void blankRefreshIsRejected() {
    assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh("  "));
    assertThrows(InvalidRefreshTokenException.class, () -> authService.refresh(null));
  }
}
