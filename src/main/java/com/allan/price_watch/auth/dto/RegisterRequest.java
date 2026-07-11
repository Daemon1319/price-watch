package com.allan.price_watch.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code max = 72} on password isn't arbitrary — bcrypt (the default
 * algorithm behind {@code SecurityConfig}'s {@code PasswordEncoder}) only
 * considers the first 72 bytes of its input and silently ignores the rest.
 * Rejecting longer passwords up front is clearer than letting someone set a
 * 100-character password that bcrypt quietly truncates to 72.
 */
public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 72) String password) {
}