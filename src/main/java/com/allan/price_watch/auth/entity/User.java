package com.allan.price_watch.auth.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registered account; password is stored hashed only. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

  @Id
  @Generated(event = EventType.INSERT)
  @Column(name = "id", insertable = false, updatable = false, nullable = false)
  private UUID id;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Generated(event = EventType.INSERT)
  @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
  private Instant createdAt;

  @Generated(event = {EventType.INSERT, EventType.UPDATE})
  @Column(name = "updated_at", insertable = false, updatable = false, nullable = false)
  private Instant updatedAt;
}
