package com.allan.price_watch.security;

import java.io.IOException;
import java.util.UUID;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs once per request, ahead of {@code UsernamePasswordAuthenticationFilter}
 * (see {@code SecurityConfig}). Populates the {@code SecurityContext} purely
 * from the JWT's claims — no database lookup here, that's the point of
 * stateless auth. A controller can get the current user's id via
 * {@code Authentication.getPrincipal()}, cast to {@code UUID}.
 *
 * <p>Missing or invalid tokens are <em>not</em> rejected here — the filter
 * just lets the request continue unauthenticated, and
 * {@code authorizeHttpRequests()} in {@code SecurityConfig} is what actually
 * decides whether that's a problem for the requested path. This keeps the
 * filter itself simple and keeps the "what needs auth" decision in one
 * place instead of split across two classes.
 *
 * <p>No granted authorities are attached ({@code AuthorityUtils
 * .NO_AUTHORITIES}) — this app has no role-based access control, every
 * endpoint's authorization boundary is just "authenticated or not" plus the
 * per-resource ownership checks already built into the repository queries
 * (see {@code TrackedItemRepository}).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain) throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = authHeader.substring(BEARER_PREFIX.length());

    if (jwtService.isTokenValid(token)) {
      UUID userId = jwtService.extractUserId(token);

      Authentication authentication = new UsernamePasswordAuthenticationToken(
          userId, null, AuthorityUtils.NO_AUTHORITIES);

      SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    filterChain.doFilter(request, response);
  }
}