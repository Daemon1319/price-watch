package com.allan.price_watch.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;

class ClientIpResolverTest {

  @Test
  void ignoresXForwardedForWhenUntrusted() {
    ClientIpResolver resolver = new ClientIpResolver(false);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 8.8.8.8");
    when(request.getRemoteAddr()).thenReturn("10.0.0.5");

    assertEquals("10.0.0.5", resolver.resolve(request));
  }

  @Test
  void usesFirstXForwardedForHopWhenTrusted() {
    ClientIpResolver resolver = new ClientIpResolver(true);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 8.8.8.8");
    when(request.getRemoteAddr()).thenReturn("10.0.0.5");

    assertEquals("9.9.9.9", resolver.resolve(request));
  }

  @Test
  void fallsBackToRemoteAddrWhenTrustedButHeaderMissing() {
    ClientIpResolver resolver = new ClientIpResolver(true);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    assertEquals("127.0.0.1", resolver.resolve(request));
  }
}
