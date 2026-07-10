package com.allan.price_watch.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT setup — no sessions, no Spring-managed login form, no HTTP
 * Basic. {@code JwtAuthenticationFilter} is the only thing that ever
 * populates the {@code SecurityContext}, by validating the bearer token
 * directly rather than going through an {@code AuthenticationManager}/
 * {@code UserDetailsService} chain. That chain exists for
 * username+password-at-the-filter-level auth (form login, HTTP Basic); a
 * REST API that authenticates via a custom {@code /auth/login} endpoint and
 * then trusts a signed JWT on every subsequent request doesn't need it, and
 * adding it here would just be unused machinery.
 *
 * <p>Note the explicit {@code httpBasic(disable)} / {@code formLogin(disable)}
 * calls below — without them, Spring Security's default filter chain
 * behavior still enables both on top of whatever you configure, which is a
 * common surprise for JWT-only APIs (you'd suddenly get a login page or a
 * Basic auth prompt on top of your JWT filter).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .httpBasic(httpBasic -> httpBasic.disable())
        .formLogin(formLogin -> formLogin.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
            .requestMatchers("/actuator/health").permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /**
   * {@code createDelegatingPasswordEncoder()} is Spring Security's own
   * currently-recommended way to construct this bean — it defaults new
   * hashes to bcrypt (prefixing them {@code {bcrypt}}) while remaining able
   * to verify other formats. That prefix scheme is what makes a future
   * migration to Argon2id (OWASP's current recommendation for new systems)
   * a config change instead of a data migration: add BouncyCastle, register
   * an {@code argon2} encoder in the map, flip {@code idForEncode}, and
   * existing bcrypt hashes keep verifying correctly until each user's next
   * login re-hashes them. Not needed for this project's scope now, but
   * worth knowing the upgrade path costs nothing structurally later.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}