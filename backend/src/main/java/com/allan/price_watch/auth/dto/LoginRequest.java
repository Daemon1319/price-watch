package com.allan.price_watch.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Login credentials payload. */
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password) {
}