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

/**
 * A registered account. {@code passwordHash} holds the bcrypt output only —
 * plaintext passwords are never stored or logged.
 *
 * <p>{@code id}, {@code createdAt}, and {@code updatedAt} are all generated
 * by the database (see {@code V1__init_schema.sql}: {@code DEFAULT
 * uuidv7()} and the {@code set_updated_at} trigger), not by Hibernate.
 * {@code @Generated} tells Hibernate to leave these columns out of the
 * INSERT/UPDATE statements entirely and read back whatever Postgres
 * actually generated afterward, instead of Hibernate assigning its own
 * client-side UUID and silently ignoring the column default.
 *
 * <p>Deliberately no {@code @OneToMany} back to {@code TrackedItem} or
 * {@code RefreshToken} here — both are queried directly by {@code userId}
 * through their own repositories. Keeping User free of owned collections
 * means touching a User never risks accidentally loading their entire
 * tracked-item list or token history along with it.
 */
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