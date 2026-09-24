# Architecture

## Overview

```mermaid
flowchart LR
    U[Browser<br/>Angular storefront + back office] -->|HTTPS| N[Nginx<br/>static files + load balancer]
    N -->|/api round robin| A1[API replica 1<br/>Spring Boot]
    N -->|/api round robin| A2[API replica 2<br/>Spring Boot]
    A1 & A2 --> P[(PostgreSQL<br/>catalog, stock ledger, orders)]
    A1 & A2 --> R[(Redis<br/>cache + rate limits)]
    A1 & A2 -.->|facts only| AI[AI provider<br/>Gemini / OpenAI]
```

No replica holds anything of its own: the session lives in Redis and every other piece of state in
PostgreSQL or Redis. Any replica can answer any request, so capacity grows by adding replicas
(`docker compose up -d --scale api=4`).

## Modules (backend)

| Package | Responsibility |
|---|---|
| `catalog` | Products, categories, search with composable filters, soft delete, bulk CSV import |
| `inventory` | Stock per product (on hand, reserved, available), append-only movement ledger, flash sale simulator |
| `orders` | Checkout, simulated payment, cancellation, automatic expiry of unpaid reservations |
| `security` | Cookie sessions, CSRF, roles, rate limiting, security headers |
| `audit` | Who changed what and when (prices, stock, logins, AI usage) |
| `analytics` | KPIs, sales velocity, days of cover, restock suggestions |
| `ai` | Provider-agnostic AI client, report generation, fallback writer |

## Key decisions

### 1. Overselling is prevented by the database
Checkout reserves stock with a single conditional statement:

```sql
UPDATE inventory_items
   SET reserved = reserved + :qty, version = version + 1
 WHERE product_id = :id AND (on_hand - reserved) >= :qty
```

The check and the write are atomic, so two buyers can never take the last unit, even on different
replicas. Less contended writes (receipts, adjustments, payment) use **optimistic locking**
(`@Version`) with an automatic retry. Multi-product checkouts lock rows in product-id order to avoid
deadlocks. Proven by `InventoryConcurrencyTest` and the flash sale simulator (200 concurrent buyers).

### 2. Reservations expire
Unpaid orders hold stock for 15 minutes. A scheduled job releases expired reservations; the order
row version guarantees that only one replica expires each order.

### 3. Stock is a ledger, not a number
Every change writes a `stock_movements` row (receipt, adjustment, reservation, release, sale) with
the resulting quantities. Current stock can always be explained and audited.

### 4. Catalog and inventory are separate tables
Stock changes on every sale while product data rarely changes. Keeping them apart means a price edit
never conflicts with a checkout.

### 5. Caching only where it is safe
| Cache | TTL | Why |
|---|---|---|
| Categories | 1 h | Almost never change |
| Dashboard | 60 s | Expensive aggregations, a minute of staleness is fine |
| AI reports | 10 min | Each call costs money and seconds |

Stock is never cached. In Docker the cache is Redis, shared by every replica.

### 6. Security
- Session in Redis behind a `HttpOnly` cookie, CSRF double-submit token, BCrypt passwords,
  role-based access (`ADMIN`, `OPERATOR`, `CUSTOMER`). See
  [ADR-008](adr/ADR-008-sessions-over-jwt.md) for why the token left the browser's storage.
- Rate limiting per IP: 10 login attempts/min (brute force), 300 API calls/min. Stored in Redis so the
  limit holds across replicas. Failed sign-ins are also counted per account, which a per-address
  limit does not catch when the attacker has a list of addresses.
- Passwords follow NIST 800-63B-4: at least 12 characters, no composition rules, no forced expiry,
  and a check against Have I Been Pwned by k-anonymity, which never sends the password.
- Staff accounts carry a second factor (TOTP, RFC 6238) with single-use recovery codes. The password
  alone opens nothing once it is enrolled, and the shared secret is encrypted at rest.
- Constant-time login (no user enumeration), 404 instead of 403 for other customers' orders.
- Audit log for sensitive actions; failed logins are recorded even though the request fails.
- Validation on every input, uniform error body, no stack traces in responses.

### 7. AI never invents numbers
The backend computes the facts (KPIs, velocity, days of cover, suggested order quantities) and sends
only those facts to the model, with instructions to use no other figures. The model never touches
the database. If the provider is slow, down or out of quota, a rule-based writer answers instead.
AI calls run outside database transactions so they never hold a pooled connection.

### 8. Bulk operations run in the background
CSV imports return `202 Accepted` with a job id and are processed in batches of 200, each in its own
transaction, on a bounded thread pool. Job progress is stored in the database because the status
request may reach a different replica.

## Module boundaries

The application is a modular monolith. Each package directly under `co.gamestore` is an application
module: it exposes the types in its own top-level package and keeps everything else in nested
packages that no other module may touch. `common` is the shared kernel.

`ModularityTests` fails the build when a module reaches into another module's internals or when two
modules end up depending on each other. The catalog needed stock figures and the inventory needed
products, which is a cycle, so the catalog now talks to the `StockPort` interface in the shared
kernel and the inventory module implements it.

The same test regenerates the diagrams and the module canvases in [`modules/`](modules/), so the
picture cannot drift from the code. `components.puml` renders with PlantUML and the C4 library.

Domain events are written to `event_publication` in the same transaction as the change that produced
them, which is what makes a failed listener replayable instead of lost.

## Scaling further (next steps)
- Read replicas for catalog and analytics queries.
- Message broker (Kafka / RabbitMQ) for order events: emails, invoicing and analytics consume them
  asynchronously.
- Materialized views or a data warehouse for analytics at millions of orders.
- CDN for static assets and product images.
- Distributed lock (ShedLock) for scheduled jobs, observability with Prometheus + Grafana.
