# PriceWatch

A product price-tracking client built with **Next.js 16**, **React 19**, and **TypeScript**. Talks to the PriceWatch Spring API for auth, tracked items, product details, and price history.

## Modules

| Module | Route / API | What It Does |
|--------|-------------|--------------|
| Auth | `/login`, `/register` | Register, login, logout, JWT + refresh cookie session |
| Dashboard | `/` | Snapshot of tracked items and recent price drops |
| Tracked Items | `/items`, `/items/[id]` | Add Uniqlo URLs, pick **color + size**, set thresholds, filter by status |
| Products | `/products/[id]` | Product/SKU detail, stock, and price history |

### Uniqlo variants

Tracking is **per color + size**, not the whole product page.

1. Paste a Uniqlo product URL on `/items`
2. Click **Load colors & sizes** (`GET /api/v1/products/variants`)
3. Choose color (e.g. Black `COL09`) and size (e.g. M `SMA004`)
4. Start tracking — POST includes `colorCode` + `sizeCode`

List and detail screens show the selected variant (e.g. `Black / M`).

## Tech Stack

Next.js 16 · React 19 · TypeScript 5 · Tailwind CSS 4 · Recharts

## Project Structure

```
src/features/
├── auth/                # Login & registration screens
├── dashboard/           # Home summary of tracking activity
├── items/               # Tracked item list & detail
└── products/            # Product detail & price history
```
