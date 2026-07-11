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

## API Endpoints

| Method | Endpoint                                      | Description                                      |
|--------|-----------------------------------------------|--------------------------------------------------|
| POST   | `/api/v1/auth/register`                       | Create account; returns access token + refresh cookie |
| POST   | `/api/v1/auth/login`                          | Login; returns access token + refresh cookie     |
| POST   | `/api/v1/auth/refresh`                        | Rotate tokens from refresh cookie (or body)      |
| POST   | `/api/v1/auth/logout`                         | Revoke refresh token and clear cookie            |
| POST   | `/api/v1/tracked-items`                       | Start tracking a product URL (+ colorCode/sizeCode for Uniqlo) |
| GET    | `/api/v1/tracked-items`                       | List your tracked items (paginated)              |
| GET    | `/api/v1/tracked-items/{id}`                  | Get one tracked item                             |
| PATCH  | `/api/v1/tracked-items/{id}`                  | Update threshold / notify / pause settings       |
| DELETE | `/api/v1/tracked-items/{id}`                  | Stop tracking                                    |
| GET    | `/api/v1/products/variants?url=`              | List Uniqlo color/size SKUs for a product URL     |
| GET    | `/api/v1/products/{id}`                       | Product snapshot (price, stock, variant, health) |
| GET    | `/api/v1/products/{id}/price-history`         | Price history for a product                      |
| POST   | `/api/v1/products/{id}/reenable-checks`       | Re-enable checks after scrape failures           |
| GET    | `/api/v1/dashboard/summary`                   | Dashboard summary (recent drops, counts)         |
| GET    | `/actuator/health`                            | Health check                                     |

### Uniqlo size / color codes

| Kind | Pattern | Examples |
|------|---------|----------|
| Alpha sizes | `SMA00x` | XS=`SMA002` … 3XL=`SMA008` |
| Waist inches | `INSxxx` | 29"=`INS029` (product-specific) |
| Colors | `COLxx` or bare digits | `COL09` / `09` / `9` → Black |

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
