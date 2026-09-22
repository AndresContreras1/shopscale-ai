# ShopScale AI

Demo e-commerce platform focused on the problems that show up when an online store grows:
**product and inventory management, security, scalability, and AI-assisted reporting.**

> Work in progress: features are delivered through pull requests, one per capability.

## Stack

| Layer | Technology |
|---|---|
| API | Java 17, Spring Boot 3.5, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 16 (H2 in-memory for local runs and tests) |
| Docs | OpenAPI / Swagger UI |
| Infra | Docker Compose |

## Run locally

**Option A: no dependencies (H2 in-memory)**

```bash
cd backend
./mvnw spring-boot:run
```

**Option B: full stack with Docker**

```bash
docker compose up --build
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Roadmap

- [x] Project setup
- [x] Catalog API (products, categories, filters, pagination)
- [x] Inventory management (stock ledger, reservations, optimistic locking)
- [ ] Security (JWT, roles, rate limiting, audit log)
- [ ] Orders and checkout
- [ ] AI reports (Gemini / OpenAI / mock)
- [ ] Angular storefront
- [ ] Admin dashboard
- [ ] Scalability (Redis cache, load balancing, async bulk import)
- [ ] CI and load testing
