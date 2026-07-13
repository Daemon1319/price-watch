# PriceWatch

Price-tracking product: **Spring Boot API** + **Next.js** client.

| Path | What |
|------|------|
| [`backend/`](./backend) | REST API, scrapers, auth, RabbitMQ pipeline |
| [`frontend/`](./frontend) | Next.js app (Vercel Root Directory = `frontend`) |

## Quick start

### Backend

```bash
cd backend
# start Postgres / Redis / RabbitMQ via compose, then:
./mvnw spring-boot:run
```

See [`backend/README.md`](./backend/README.md) for API details.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Point the app at your API with env vars (see frontend README). Deploy only this folder on Vercel.

## History

- Backend history lives under `backend/` (renamed from repo root).
- Frontend history was merged from `price-watch-frontend` via `git subtree` into `frontend/`.
