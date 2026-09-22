# ShopScale AI

Demo e-commerce platform focused on the problems that show up when an online store grows:
**product and inventory management, security, scalability, and AI-assisted reporting.**

> Work in progress: features are delivered through pull requests, one per capability.

## Stack

| Layer | Technology |
|---|---|
| API | Java 17, Spring Boot 3.5, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 16 (H2 in-memory for local runs and tests) |
| Frontend | Angular 20 (standalone components, signals, lazy routes) |
| Docs | OpenAPI / Swagger UI |
| Infra | Docker Compose |

## Run locally

**Option A: no dependencies (H2 in-memory)**

```bash
cd backend
./mvnw spring-boot:run
```

Then, in another terminal, the storefront (proxies `/api` to `localhost:8080`):

```bash
cd frontend
npm install
npm start
```

Open http://localhost:4200.

**Option B: full stack with Docker**

```bash
docker compose up --build
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## AI reports

The backend computes the numbers (KPIs, days of stock cover, suggested restock quantities) and the AI
only turns them into an executive report, so reports never contain invented figures.

| Variable | Default | Purpose |
|---|---|---|
| `AI_PROVIDER` | `mock` | `mock`, `gemini` or `openai` |
| `GEMINI_API_KEY` / `GEMINI_MODEL` | - / `gemini-3.8-flash` | Google Gemini |
| `OPENAI_API_KEY` / `OPENAI_MODEL` | - / `gpt-4o-mini` | OpenAI |

Without an API key, or if the provider fails (timeout, quota), a rule-based writer answers instead.

## Demo accounts

Seeded automatically on an empty database. **Demo only.**

| Role | Email | Password | Can do |
|---|---|---|---|
| ADMIN | admin@shopscale.dev | Admin123! | Everything: catalog, prices, inventory, reports, audit |
| OPERATOR | operator@shopscale.dev | Operator123! | Inventory only |
| CUSTOMER | customer@shopscale.dev | Customer123! | Storefront and own orders |

## Roadmap

- [x] Project setup
- [x] Catalog API (products, categories, filters, pagination)
- [x] Inventory management (stock ledger, reservations, optimistic locking)
- [x] Security (JWT, roles, rate limiting, audit log)
- [x] Orders and checkout
- [x] AI reports (Gemini / OpenAI / mock)
- [x] Angular storefront
- [x] Admin dashboard
- [ ] Scalability (Redis cache, load balancing, async bulk import)
- [ ] CI and load testing
