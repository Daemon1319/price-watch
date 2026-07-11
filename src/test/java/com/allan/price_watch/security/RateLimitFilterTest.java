package com.allan.price_watch.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

class RateLimitFilterTest {

  @Test
  void strictAuthOnlyOnLoginAndRegisterPost() {
    assertTrue(RateLimitFilter.isStrictAuthEndpoint(request("POST", "/api/v1/auth/login")));
    assertTrue(RateLimitFilter.isStrictAuthEndpoint(request("POST", "/api/v1/auth/register")));
    assertFalse(RateLimitFilter.isStrictAuthEndpoint(request("POST", "/api/v1/auth/refresh")));
    assertFalse(RateLimitFilter.isStrictAuthEndpoint(request("POST", "/api/v1/auth/logout")));
    assertFalse(RateLimitFilter.isStrictAuthEndpoint(request("GET", "/api/v1/auth/login")));
    assertFalse(RateLimitFilter.isStrictAuthEndpoint(request("POST", "/api/v1/tracked-items")));
  }

  @Test
  void stripsContextPath() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/app/api/v1/auth/login");
    when(request.getContextPath()).thenReturn("/app");
    assertTrue(RateLimitFilter.isStrictAuthEndpoint(request));
  }

  private static HttpServletRequest request(String method, String uri) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn(method);
    when(request.getRequestURI()).thenReturn(uri);
    when(request.getContextPath()).thenReturn("");
    return request;
  }
}
