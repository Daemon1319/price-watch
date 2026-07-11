package com.allan.price_watch.security;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Redis rate limiter: per-user when authenticated, per-IP otherwise. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

  private final ProxyManager<String> proxyManager;
  private final Supplier<BucketConfiguration> apiConfigurationSupplier;
  private final Supplier<BucketConfiguration> authConfigurationSupplier;
  private final ClientIpResolver clientIpResolver;

  public RateLimitFilter(
      ProxyManager<String> proxyManager,
      @Qualifier("apiRateLimitConfiguration") BucketConfiguration apiRateLimitConfiguration,
      @Qualifier("authRateLimitConfiguration") BucketConfiguration authRateLimitConfiguration,
      ClientIpResolver clientIpResolver) {
    this.proxyManager = proxyManager;
    this.apiConfigurationSupplier = () -> apiRateLimitConfiguration;
    this.authConfigurationSupplier = () -> authRateLimitConfiguration;
    this.clientIpResolver = clientIpResolver;
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    return "OPTIONS".equalsIgnoreCase(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain) throws ServletException, IOException {

    boolean authEndpoint = isStrictAuthEndpoint(request);
    String key = rateLimitKey(request, authEndpoint);
    Supplier<BucketConfiguration> config = authEndpoint
        ? authConfigurationSupplier
        : apiConfigurationSupplier;

    try {
      var bucket = proxyManager.builder().build(key, config);
      if (!bucket.tryConsume(1)) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429}");
        return;
      }
    } catch (RuntimeException e) {
      // Fail open if Redis is unavailable.
      log.warn("Rate limit check failed for {}; allowing request: {}", key, e.getMessage());
    }

    filterChain.doFilter(request, response);
  }

  /** True for login/register, which use a stricter rate-limit bucket. */
  static boolean isStrictAuthEndpoint(HttpServletRequest request) {
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    String path = request.getRequestURI();
    if (path == null) {
      return false;
    }
    String context = request.getContextPath();
    if (context != null && !context.isEmpty() && path.startsWith(context)) {
      path = path.substring(context.length());
    }
    return "/api/v1/auth/login".equals(path) || "/api/v1/auth/register".equals(path);
  }

  private String rateLimitKey(HttpServletRequest request, boolean authEndpoint) {
    String prefix = authEndpoint ? "rate-limit:auth:" : "rate-limit:";
    return prefix + resolveClientKey(request);
  }

  private String resolveClientKey(HttpServletRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UUID userId) {
      return "user:" + userId;
    }
    return "ip:" + clientIpResolver.resolve(request);
  }
}
