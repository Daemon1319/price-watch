package com.allan.price_watch.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Browser SPA (Next.js) runs on a different origin than this API. Origins come
 * from {@code app.cors.allowed-origins} — local is hard-coded in
 * {@code application.yaml}; prod is {@code ${CORS_ALLOWED_ORIGINS}} at deploy.
 *
 * <p>{@code allowCredentials=true} is required so the SPA can send/receive the
 * HttpOnly refresh cookie via {@code fetch(..., { credentials: "include" })}.
 * Allowed origins must be explicit (never {@code *}) when credentials are on.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    WebConfig.CorsProperties.class,
    com.allan.price_watch.auth.RefreshCookieProperties.class
})
public class WebConfig {

  @Bean
  public CorsFilter corsFilter(CorsProperties props) {
    CorsConfiguration config = new CorsConfiguration();
    String raw = props.allowedOrigins() != null ? props.allowedOrigins() : "http://localhost:3000";
    List<String> origins = java.util.Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
    config.setAllowedOrigins(origins);
    config.setAllowCredentials(true);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    // SPA reads Retry-After on 429 to drive cooldown UI (otherwise browser hides it).
    config.setExposedHeaders(List.of("Retry-After"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsFilter(source);
  }

  @ConfigurationProperties("app.cors")
  public record CorsProperties(String allowedOrigins) {
  }
}
