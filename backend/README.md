# PriceWatch

A price-tracking REST API built with **Spring Boot 4**. Users subscribe to product URLs (Uniqlo first, site scrapers are pluggable), get scheduled scrapes over **RabbitMQ**, and receive **email alerts** on price drops or restocks via a transactional outbox.

## Key Features

- **JWT Auth + Refresh Cookies** — Access tokens in the response body; HttpOnly `pw_refresh` cookie for rotate-on-refresh and logout revocation
- **Tracked Items** — Subscribe by product URL with optional price threshold and restock-only notify mode
- **Uniqlo variants** — Track a specific color + size (e.g. `COL09` black, `SMA004` M, `INS029` 29"); list options via `/products/variants`
- **Async Scraping Pipeline** — Cron enqueues product checks → RabbitMQ workers scrape with Redis product locks and per-domain throttling
- **Transactional Outbox** — Price/stock changes write outbox rows in the same DB transaction; a ShedLock-guarded relay publishes to the notification queue
- **Rate Limiting** — Bucket4j + Redis token buckets (stricter limits on login/register)
- **Observability** — Actuator health, metrics, and Prometheus scrape endpoint

## Tech Stack

Java 21 · Spring Boot 4 · Spring Security · JWT · Spring Data JPA · PostgreSQL · Flyway · Redis · RabbitMQ · Bucket4j · ShedLock · Spring Mail · Docker Compose · Micrometer/Prometheus

---

## Running the Backend Locally

### Prerequisites

| Tool                                                              | Version | Why                                     |
| ----------------------------------------------------------------- | ------- | --------------------------------------- |
| [Java JDK](https://adoptium.net/)                                 | 21+     | Compile & run the app                   |
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) | Latest  | Runs Postgres, Redis, RabbitMQ, MailHog |

That's it. No local Postgres/Redis/RabbitMQ installs needed — Docker Compose handles everything.

### Step 1 — Clone the repo

You only need the `backend/` folder. The frontend is already deployed on Vercel (see [Frontend](#frontend) below).

```bash
git clone https://github.com/YOUR_USERNAME/price_watch.git
cd price_watch/backend
```

### Step 2 — Start infrastructure

```bash
docker compose up -d
```

This pulls and starts four containers:

| Service  | Image                            | Host Port                    | Purpose                             |
| -------- | -------------------------------- | ---------------------------- | ----------------------------------- |
| Postgres | `postgres:18.4-alpine`           | **5433**                     | Application database                |
| Redis    | `redis:8.8-alpine`               | 6379                         | Rate limiting + caching             |
| RabbitMQ | `rabbitmq:4.3-management-alpine` | 5672 (AMQP) / **15672** (UI) | Async scraping queue                |
| MailHog  | `mailhog/mailhog:v1.0.1`         | 1025 (SMTP) / **8025** (UI)  | Catches notification emails locally |

> **Why port 5433?** Many Windows machines already have a local PostgreSQL on 5432. Using 5433 avoids conflicts.

Wait ~20s for health checks to pass. Verify with:

```bash
docker compose ps
```

All services should show `healthy`.

### Step 3 — Run the app

```bash
./mvnw spring-boot:run
```

- The default profile is `local` — it connects to the Docker Compose stack automatically.
- Flyway runs all database migrations on first boot (creates tables, indexes, etc.).
- App starts on **http://localhost:8080**.

### Step 4 — Verify it works

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

Register a test user:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'
```

You should get back an `accessToken` in the JSON response.

### Step 5 — Run tests

```bash
./mvnw test
```

- 99 tests total. Docker must be running (Testcontainers spins up ephemeral containers for integration tests).
- Tests that need Docker skip cleanly if it's unavailable.

### Useful UIs

| URL                    | Credentials                 | What                                   |
| ---------------------- | --------------------------- | -------------------------------------- |
| http://localhost:15672 | `pricewatch` / `pricewatch` | RabbitMQ management (queues, messages) |
| http://localhost:8025  | —                           | MailHog (view notification emails)     |

### Building the JAR

```bash
./mvnw -DskipTests package
```

This produces `target/price_watch-0.0.1-SNAPSHOT.jar` — a self-contained executable JAR.

Run it directly:

```bash
java -jar target/price_watch-0.0.1-SNAPSHOT.jar
```

---

## Running with Cloud Services (`local-cloud` profile)

Instead of Docker Compose, you can point the app at managed cloud services. This is how the production-like setup works locally.

> **You don't have to use the same providers I use.** The app just needs:
>
> - A **PostgreSQL 18+** database (Neon, Supabase, AWS RDS, self-hosted — anything)
> - A **Redis** instance (Upstash, AWS ElastiCache, self-hosted — anything)
> - A **RabbitMQ** broker (CloudAMQP, AWS Amazon MQ, self-hosted — anything)
> - An **SMTP server** for emails (Brevo, Mailgun, Gmail SMTP, self-hosted — anything)
>
> Fill in whatever connection strings you have. The env vars below are provider-agnostic.

### Environment Variables

The `local-cloud` profile reads everything from environment variables. No secrets live in `application.yaml`.

| Variable                     | Example                                             | What                                   |
| ---------------------------- | --------------------------------------------------- | -------------------------------------- |
| `SPRING_PROFILES_ACTIVE`     | `local-cloud`                                       | Activates the cloud profile            |
| `SERVER_PORT`                | `9999`                                              | Port the app listens on                |
| `SPRING_DATASOURCE_URL`      | `jdbc:postgresql://...neon.tech/db?sslmode=require` | Neon Postgres connection string        |
| `SPRING_DATASOURCE_USERNAME` | `neondb_owner`                                      | DB user                                |
| `SPRING_DATASOURCE_PASSWORD` | `npg_...`                                           | DB password                            |
| `SPRING_DATA_REDIS_URL`      | `rediss://default:TOKEN@...upstash.io:6379`         | Upstash Redis (TLS)                    |
| `SPRING_RABBITMQ_ADDRESSES`  | `amqps://user:pass@...cloudamqp.com/vhost`          | CloudAMQP (TLS)                        |
| `SPRING_MAIL_HOST`           | `smtp-relay.brevo.com`                              | Brevo SMTP host                        |
| `SPRING_MAIL_PORT`           | `587`                                               | SMTP port                              |
| `SPRING_MAIL_USERNAME`       | `xxxxx@smtp-brevo.com`                              | Brevo SMTP login                       |
| `SPRING_MAIL_PASSWORD`       | `xsmtpsib-...`                                      | Brevo SMTP key (not API key)           |
| `MAIL_FROM`                  | `you@gmail.com`                                     | Sender address for notification emails |
| `JWT_SECRET`                 | (base64, ≥ 32 bytes)                                | Signs access tokens                    |
| `CORS_ALLOWED_ORIGINS`       | `https://price-watch-meow.vercel.app`               | Allowed frontend origin                |
| `REFRESH_COOKIE_SECURE`      | `false`                                             | Set `false` for localhost (no HTTPS)   |
| `REFRESH_COOKIE_SAME_SITE`   | `Lax`                                               | Cookie SameSite for same-machine usage |

### Script-based runner (Windows PowerShell)

A template is provided at [`run-local-cloud.example.ps1`](run-local-cloud.example.ps1). Copy it and fill in your credentials:

```powershell
cp run-local-cloud.example.ps1 run-local-cloud.ps1
# Edit run-local-cloud.ps1 with your real credentials
```

> `run-local-cloud.ps1` is gitignored — never commit real credentials.

### Usage

```powershell
# Foreground (see logs in terminal, Ctrl+C to stop)
.\run-local-cloud.ps1

# Background (no console window, logs to logs\price-watch.log)
.\run-local-cloud.ps1 -Hidden

# Stop the background process
.\stop-local-cloud.ps1
```

### Double-click launcher (optional)

Create `start-local-cloud-hidden.vbs` next to the script for a no-console double-click start:

```vbs
' Double-click to start price-watch with no console window.
Option Explicit
Dim sh, dir, cmd
Set sh = CreateObject("WScript.Shell")
dir = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
cmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & dir & "\run-local-cloud.ps1"" -Hidden"
sh.Run cmd, 0, False
```

### Frontend

The frontend is already deployed on **[Vercel](https://price-watch-meow.vercel.app/)** — you don't need to clone or set it up. It's configured to talk to the backend at `http://localhost:9999`, which is the port the `local-cloud` scripts use.

If you're using the Docker Compose setup (Step 2–4 above), the app runs on port **8080** instead.

---

## API Endpoints

| Method | Endpoint                                | Description                                                    |
| ------ | --------------------------------------- | -------------------------------------------------------------- |
| POST   | `/api/v1/auth/register`                 | Create account; returns access token + refresh cookie          |
| POST   | `/api/v1/auth/login`                    | Login; returns access token + refresh cookie                   |
| POST   | `/api/v1/auth/refresh`                  | Rotate tokens from refresh cookie (or body)                    |
| POST   | `/api/v1/auth/logout`                   | Revoke refresh token and clear cookie                          |
| POST   | `/api/v1/tracked-items`                 | Start tracking a product URL (+ colorCode/sizeCode for Uniqlo) |
| GET    | `/api/v1/tracked-items`                 | List your tracked items (paginated)                            |
| GET    | `/api/v1/tracked-items/{id}`            | Get one tracked item                                           |
| PATCH  | `/api/v1/tracked-items/{id}`            | Update threshold / notify / pause settings                     |
| DELETE | `/api/v1/tracked-items/{id}`            | Stop tracking                                                  |
| GET    | `/api/v1/products/variants?url=`        | List Uniqlo color/size SKUs for a product URL                  |
| GET    | `/api/v1/products/{id}`                 | Product snapshot (price, stock, variant, health)               |
| GET    | `/api/v1/products/{id}/price-history`   | Price history for a product                                    |
| POST   | `/api/v1/products/{id}/reenable-checks` | Re-enable checks after scrape failures                         |
| GET    | `/api/v1/dashboard/summary`             | Dashboard summary (recent drops, counts)                       |
| GET    | `/actuator/health`                      | Health check                                                   |

### Uniqlo size / color codes

| Kind         | Pattern                | Examples                        |
| ------------ | ---------------------- | ------------------------------- |
| Alpha sizes  | `SMA00x`               | XS=`SMA002` … 3XL=`SMA008`      |
| Waist inches | `INSxxx`               | 29"=`INS029` (product-specific) |
| Colors       | `COLxx` or bare digits | `COL09` / `09` / `9` → Black    |

**Reference data** (labels only — not scrape rules) lives in:

`src/main/resources/scraper/uniqlo/catalog.json`

Edit that file to add colors/sizes. Business logic loads it via `UniqloCatalog`. Live product names from Uniqlo’s API still win when present. Prefer `GET /products/variants` for what a given item actually sells.

Track either via query params on the URL or body fields (body wins):

```json
{
  "url": "https://www.uniqlo.com/ph/en/products/E471809-000",
  "colorCode": "09",
  "sizeCode": "SMA004",
  "priceThreshold": 1500,
  "notifyOnRestockOnly": false
}
```
