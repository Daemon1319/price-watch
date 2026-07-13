package com.allan.price_watch.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/** CORS config so the SPA can call the API with credentials. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    WebConfig.CorsProperties.class,
    com.allan.price_watch.auth.RefreshCookieProperties.class
})
public class WebConfig {

  /** Builds a CorsFilter from app.cors.allowed-origins. */
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
    // Expose Retry-After so the SPA can show 429 cooldown UI.
    config.setExposedHeaders(List.of("Retry-After"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsFilter(source);
  }

  @ConfigurationProperties("app.cors")
  public record CorsProperties(String allowedOrigins) {
  }
}
