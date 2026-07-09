package com.allan.price_watch.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.allan.price_watch.auth.entity.User;

/**
 * {@code IgnoreCase} here compiles down to a {@code lower(email) = lower(?)}
 * comparison, which lines up with the {@code users_email_lower_idx}
 * functional index from {@code V1__init_schema.sql} — Postgres can use that
 * index for both of these instead of scanning the table.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);
}