# Master plan: from ShopScale AI demo to a real console store

> Working name: **Game Store** (placeholder, subject to change; candidates in §1.4). Market: Colombia first, international-ready. One vendor, one repo, one developer.
> Written September 2026 from three inputs: the current codebase, the Desarrollo Web wiki of the vault (what Andres already masters), and eight research reports in [`docs/research/`](research/) with sources and glossaries.

**How to read the tags.** Every technique in this plan is marked so nothing enters the project unnamed:

| Tag | Meaning |
|---|---|
| 📗 | Covered by the vault (the note is named). Already defensible. |
| 🟡 | Partially in the vault: the base exists, one step is missing. |
| 🆕 | New concept. Study it before or while building it; the research report holds the glossary and sources. |
| ⚖️ | A deliberate deviation from what the vault teaches, with the reason written down. |

---

## 1. Vision, scope and success criteria

### 1.1 What we are building
A production e-commerce for a **console and gaming store with an in-house repair workshop**: consoles (new, refurbished, used), controllers, accessories, physical games, digital keys/gift cards, spare parts, and **repair services** sold in the same checkout. Customers buy online, pay with Colombian methods, get shipped anywhere in Colombia, book or mail in repairs, and follow every order and ticket by WhatsApp and email. Staff run catalog, stock, fulfillment, repairs, support and finance from one back office, with AI doing the repetitive work under human control.

### 1.2 Goals
1. **Sell for real**: legal in Colombia (consumer law, data protection, electronic invoicing), paid through real gateways, shipped by real carriers.
2. **Never oversell, never lose money silently**: reservations, idempotent payments, reconciliation, audit trail.
3. **Run the workshop**: repair tickets with the legal receipt, quotes, parts consumption from stock, warranties.
4. **Scale without rewriting**: stateless API replicas, shared state in PostgreSQL/Redis, observable, backed up, deployable in minutes.
5. **Look professional**: a storefront with real design technique (SSR, design system, photography, accessibility), not a template.
6. **AI that pays for itself**: search, listings, reports, repair pre-diagnosis, support; always grounded on computed facts and reviewed by a person.
7. **International base**: currency, tax, payment, carrier and language are abstractions from day one; Colombia is the first adapter of each.

### 1.3 Non-goals (for now)
Marketplace with third-party sellers · console modding/chipping (legal risk) · mobile apps · microservices · Kubernetes · multi-currency pricing before there is a second market.

### 1.4 Brand and naming (decision pending)
The demo name "ShopScale AI" goes away. Until the brand is decided the project is called **Game Store**. Candidates to evaluate later, in order of preference:

| Option | Why | Domain hints (DNS check on 2026-09-23, not a registrar check) |
|---|---|---|
| **Respawn** | Gaming word for "come back to life": the console dies, the workshop respawns it. Short, works in Spanish and English. | `respawn.co` taken · `respawn.com.co`, `respawnlab.co`, `respawnstore.co` free |
| Checkpoint | "Your checkpoint": the place you save your game and your gear. | `checkpointgaming.co`, `checkpoint.com.co` free |
| 1UP Lab | Extra life; lab = workshop. | `1uplab.co` free |

The rest of this document uses **Game Store**. Phase 0 renames the repo, the Java package (`co.gamestore` as a placeholder), the Angular app and every visible string; the final brand and domains can be applied in a later, smaller rename once they are registered.

### 1.5 Definition of "real" (launch gate, see §11)
The store may sell to the public only when every item of the launch checklist is green: legal pages and consents, real payments with reconciliation, electronic invoicing, shipping with tracking, backups with a tested restore, monitoring and alerts, security controls of §7, and the test suites of §8 passing in CI.

---

## 2. Starting point: what ShopScale AI already gives us

| Area | Keep as is | Keep but evolve | Replace |
|---|---|---|---|
| Catalog | Products, categories, filters with Specifications 🆕, pagination cap 📗 *Paginacion y PageResponse en APIs REST* | Variants, condition, compatibility, media (§5.1) | Hand-written seed data → real catalog + import |
| Inventory | Atomic conditional `UPDATE` 📗 *JPQL y Query*, `@Version` 📗 *Bloqueo optimista y pesimista en JPA*, append-only ledger 🆕, flash-sale simulator | Multi-warehouse readiness, serialized units, parts consumption by repairs | – |
| Orders | State machine as enum, reservation expiry job | Real order lifecycle with payments, shipments, returns (§5.3) | Simulated payment |
| Security | Roles, BCrypt 📗, rate limiting 📗 *Rate limiting*, audit log 📗 *Auditoria e historial de cambios en JPA*, constant-time login | Password policy, MFA, admin hardening | JWT in `localStorage` ⚖️ → cookie sessions (§7.1) |
| Analytics + AI | Facts computed in code, AI narrates, fallback writer, transparency panel | Spring AI, pgvector, evals (§9) | Hand-written REST clients |
| Platform | Docker, Nginx replicas, Redis cache/limits, GitHub Actions, k6 | Real hosting, observability, backups (§8) | H2 in tests → Testcontainers; `ddl-auto` → Flyway |
| Frontend | Angular 20 standalone, signals, lazy routes, guards, interceptor | SSR, design system, i18n, tests (§6) | Hand-drawn CSS and USD formatting |

The demo's strongest ideas (reservation atomicity, ledger, facts-before-AI, stacked PRs) stay the backbone.

---

## 3. The vault as the base

Gemini read all 208 notes of `Wiki/Desarrollo Web/` (backend and frontend) and cross-checked them against the code. Summary of what the vault prescribes and how this plan honors it.

### 3.1 Backend conventions we keep (📗)
- **Layers**: controller → service → repository, never skipped ("*Siempre del controlador al servicio y del servicio al repositorio*", *Patron de capas*). Inside every module of the modular monolith this rule stays verbatim; ArchUnit enforces it (*ArchUnit*).
- **Contracts**: records as DTOs, never entities on the wire, never sensitive data to the frontend (*DTOs en Spring Boot*, *MapStruct*); Bean Validation on input (*Validacion de formularios en Spring Boot*).
- **Errors**: `@RestControllerAdvice` with RFC 9457 `ProblemDetail` (*Manejo de errores en APIs REST con Spring Boot*), semantic status codes (*Codigos de estado HTTP*), stack traces off in production.
- **Security**: filter chain with `OncePerRequestFilter`, `SecurityContextHolder`, BCrypt always, explicit CORS (never `*`), JSON 401/403, IDOR prevention by scoping queries (*Cadena de filtros de Spring Security*, *Prevencion de IDOR*, *Respuestas de error en Spring Security*). Credentials only by POST.
- **Persistence**: `IDENTITY` ids ("*el id nunca es un dato de negocio*"), LAZY relations, `@Transactional(readOnly)`, `@Version` for optimistic locking and `PESSIMISTIC_WRITE` for critical stock, `@EntityGraph` + `default_batch_fetch_size` against N+1, `count` in the database not in Java, logical deletion, auditing (*Entidades JPA*, *Transactional en Spring*, *Rendimiento y escalabilidad con JPA*, *Eliminacion logica*).
- **Schema**: Flyway migrations with `ddl-auto=validate`, PostgreSQL in Docker with pinned version (never `:latest`), profiles per environment, deterministic seeds (*Migraciones de esquema con Flyway*, *PostgreSQL con Docker*, *Perfiles de Spring Boot*, *Inicializador de datos con CommandLineRunner*).
- **Capacity**: HikariCP sized to the database host, thread pool ≥ active connections, virtual threads, the database lives on its own server in production (*Pool de conexiones a la base de datos*, *Pool de hilos en Spring Boot*, *Tareas CPU intensive e IO intensive*, *Cuellos de botella*).
- **Tests**: pyramid with Mockito, `@DataJpaTest`, `@WebMvcTest`/MockMvc, `@SpringBootTest`, security tests, end-to-end, JaCoCo, SonarQube, k6; never against development or production databases (*Pruebas automatizadas en Spring Boot* and the seven test notes).
- **API docs**: springdoc with `@Operation`/`@Tag`, hidden in production when not public; never Springfox (*OpenAPI y Swagger en Spring Boot*).

### 3.2 Frontend conventions we keep (📗)
- Semantic HTML, `label for`, `alt` on every image, forms with `name` (*HTML semantico*, *Formularios HTML*, *Imagenes HTML*).
- TypeScript strict: no `var`, no `any`, null safety with `?.`/`??` (*TypeScript*, *Null safety en TypeScript*).
- Angular: standalone components, `inject()`, HTTP only in `ngOnInit`/resources never in constructors, routes ordered specific → generic, typed `Observable<T>` from services, subscribe in components, `switchMap`/`forkJoin` instead of nested subscribes, `takeUntilDestroyed` (*Componentes en Angular*, *Servicios en Angular*, *Operadores de aplanamiento en RxJS*, *Gestion de suscripciones en Angular*).
- **Reactive forms for CRUD and checkout**, template forms only for simple search (*Formularios reactivos en Angular*, *Formularios de plantilla con ngModel en Angular*). The current admin forms use `ngModel`: they migrate to reactive forms in Phase 7.
- Modern control flow `@if/@for`, functional interceptors, guards (*Interpolacion y control de flujo en Angular*, *Interceptores HTTP en Angular*).
- Responsive layout with grid/flex and media queries; Tailwind and Bootstrap are both in the vault (*Tailwind CSS*, *Responsive en Tailwind*, *Bootstrap*). The professor prefers Bootstrap and warns about Tailwind's "infierno de clases"; §6.2 explains why the storefront uses Tailwind v4 with extracted components, which is the mitigation he describes.
- "*Tampoco es bueno trabajar siempre con la última versión de todo*": this plan moves to **LTS** versions (Java 25 LTS, Spring Boot 4.1 OSS-supported, Angular 21 LTS), never to previews.

### 3.3 Deliberate deviations (⚖️)
| Vault says | Plan does | Why |
|---|---|---|
| JWT stored in `localStorage`, sent as Bearer (*JSON Web Token*, *Almacenamiento en el navegador*) | Server session in Redis + `HttpOnly` cookie + CSRF token (Spring Session, `csrf.spa()`) | RFC 10017 / OWASP: tokens readable by JavaScript are stolen by any XSS; a real store holds payment and personal data. The JWT knowledge stays useful for third-party API access later. |
| CSRF disabled "because it is a REST API with JWT" (*CSRF*) | CSRF enabled with the double-submit cookie | Cookies reintroduce CSRF, so the protection comes back. Same note, opposite branch. |
| Selenium for end-to-end (*Selenium WebDriver*) | Playwright with the Angular schematics | Faster, auto-waits, traces; the Selenium notes (explicit waits, locators by id, `data-testid`) transfer one-to-one. |
| `@CrossOrigin` per controller (*CORS*) | One `CorsConfigurationSource` + same-origin deployment behind Nginx | Central policy, no origin list drift. |
| H2 in tests (*H2 y conexion a la base de datos*) | Testcontainers PostgreSQL | Migrations, indexes and SQL functions must be tested on the real engine. |

### 3.4 Gaps the vault does not cover (🆕, all researched)
Payments and webhooks · idempotency · transactional outbox and domain events · background job queue · zero-downtime migrations · secrets management · observability (traces, metrics, logs) · SSR/SEO/structured data · Core Web Vitals · accessibility WCAG · i18n · design systems · unit tests for Angular · electronic invoicing · shipping APIs · repair-shop domain · AI engineering (RAG, evals, guardrails). Each appears in the phase where it is first needed, with its research report.

---

## 4. Target architecture

### 4.1 Shape

```mermaid
flowchart LR
    B[Browser<br/>Angular SSR storefront + back office] --> CF[Cloudflare<br/>DNS · CDN · WAF · TLS]
    CF --> N[Nginx / Caddy<br/>static + reverse proxy]
    N --> SSR[Angular SSR<br/>Node container]
    N --> A1[API replica 1]
    N --> A2[API replica 2]
    A1 & A2 --> PG[(PostgreSQL 16→18<br/>data · pgvector · jobs · outbox)]
    A1 & A2 --> R[(Redis<br/>sessions · cache · rate limits)]
    A1 & A2 --> S3[(Cloudflare R2<br/>images · labels · invoices PDF)]
    A1 & A2 -.-> W[Wompi / Mercado Pago]
    A1 & A2 -.-> C[Envia.com / Aveonline]
    A1 & A2 -.-> M[SES/Postmark · WhatsApp Cloud API]
    A1 & A2 -.-> F[Factus (DIAN e-invoicing)]
    A1 & A2 -.-> AI[Gemini / OpenAI via Spring AI]
    O[Grafana Cloud · Sentry · Better Stack] -.-> A1
```

**Style: modular monolith** 🆕 (Spring Modulith). One deployable, one database, strict module boundaries verified in tests, domain events between modules through the persisted event registry (an in-database outbox). Microservices are explicitly out of scope until there is a second team.

### 4.2 Modules (bounded contexts)

| Module | Owns | Publishes | Consumes |
|---|---|---|---|
| `catalog` | products, variants, attributes, condition, compatibility, media, categories, search index | `ProductChanged` | – |
| `inventory` | stock items per warehouse, reservations, ledger, serialized units | `StockLow`, `StockReserved` | `OrderPlaced`, `RepairQuoteApproved` |
| `ordering` | cart, checkout, orders, order lines, promotions applied | `OrderPlaced`, `OrderPaid`, `OrderCancelled` | `PaymentApproved`, `ShipmentDelivered` |
| `payments` | payment intents, attempts, provider events, refunds, disputes, reconciliation | `PaymentApproved`, `PaymentDeclined`, `RefundIssued` | `OrderPlaced` |
| `invoicing` | electronic invoices, credit notes, tax lines | `InvoiceIssued` | `PaymentApproved`, `RefundIssued` |
| `shipping` | quotes, shipments, labels, tracking, returns (RMA) | `ShipmentDelivered`, `ReturnReceived` | `OrderPaid` |
| `repairs` | devices, tickets, diagnoses, quotes, part usage, warranties, appointments | `RepairStatusChanged`, `RepairQuoteApproved` | `PaymentApproved` |
| `customers` | accounts, addresses, consents, data rights, staff roles | `CustomerRegistered` | – |
| `promotions` | coupons, campaigns, redemptions, gift cards, store credit | – | `OrderPlaced` |
| `notifications` | templates, preferences, outbox to email/WhatsApp, provider webhooks | – | every event above |
| `content` | CMS pages, policies with versions, FAQ, SEO metadata, sitemaps | – | `ProductChanged` |
| `analytics` | read models, KPIs, forecasts | – | events |
| `ai` | Spring AI clients, embeddings, assistants, evals, cost accounting | – | `ProductChanged`, `RepairStatusChanged` |
| `shared` | `Money`, ids, `Problem` types, clock, pagination, idempotency | – | – |

Inside each module the vault's layering holds: `api` (controllers, DTOs) → `application` (services) → `domain` (entities, state machines) → `infrastructure` (repositories, adapters).

### 4.3 Stack decisions

| Layer | Decision | Tag | Rationale (details in the research report) |
|---|---|---|---|
| Runtime | **Java 25 LTS**, virtual threads on | 🟡 *Pool de hilos en Spring Boot* mentions virtual threads | Boot 4.1 supports 17–26; JEP 491 fixes pinning; LTS. |
| Framework | **Spring Boot 4.1.x** (Framework 7, Security 7.1, Hibernate 7.4), **Spring Modulith 2.1** | 📗 *Spring Boot* / 🆕 Modulith | Boot 3.5 OSS support ended 2026-06-30; 4.1 is supported to mid-2027. [04] |
| Schema | **Flyway 12** with baseline + `ddl-auto=validate` | 📗 *Migraciones de esquema con Flyway* | Zero-downtime expand/contract discipline 🆕. |
| Database | **PostgreSQL 16** now, 18 when convenient (`uuidv7()`), extensions `pg_trgm`, `unaccent`, `pgvector` | 📗 *PostgreSQL con Docker* | One database for data, search MVP, jobs, outbox and vectors. |
| Cache/session | **Redis** (Valkey-compatible) via Spring Session + Spring Cache | 🟡 *Cache de primer y segundo nivel en Hibernate* | Shared by all replicas. |
| Jobs | `@Scheduled` + ShedLock for cron; **db-scheduler** for retried work | 🆕 | Postgres-backed, one table, no broker. [04] |
| Events | Modulith event publication registry (outbox) | 🆕 | At-least-once delivery inside the monolith; externalize to RabbitMQ only later. |
| Identity | Spring Security in-app, **cookie sessions**, `csrf.spa()`, TOTP + passkeys for staff, Google login for customers | ⚖️ see §3.3 · 📗 *Spring Security*, *Usuarios y roles en Spring Security* | RFC 10017, NIST 800-63B-4. [04][07] |
| Payments | **Wompi** first (hosted Web Checkout, SAQ A), **Mercado Pago** second, Addi later | 🆕 | Colombian methods (PSE, Nequi, cards, cash), signed webhooks, sandbox. [01] |
| Invoicing | **Factus** (DIAN proveedor tecnológico) behind an `InvoicingProvider` port | 🆕 | Every order > 5 UVT needs a factura electrónica. [01] |
| Shipping | **Envia.com** aggregator first, Aveonline fallback, Coordinadora direct at volume | 🆕 | Quotes, labels, tracking, COD. [02] |
| Email | **Amazon SES** (or Postmark) with Thymeleaf templates from MJML | 📗 *Thymeleaf* / 🆕 deliverability | Cheapest reliable for Java. [07] |
| WhatsApp | **Meta Cloud API** direct, Chatwoot inbox | 🆕 | Dominant channel in Colombia, cheapest utility rate. [07] |
| Search | PostgreSQL FTS (`spanish` + `unaccent`) + `pg_trgm` now; **Meilisearch** when facets hurt | 🆕 | [03] |
| Media | **Cloudflare R2** + **imgproxy**, `NgOptimizedImage` | 🆕 | Zero egress, on-the-fly WebP/AVIF. [03] |
| AI | **Spring AI 2.0**, `gemini-3.8-flash` default, `pgvector` HNSW, paid tier only | 🆕 | [06] |
| Frontend | **Angular 21 LTS** (from 20), SSR hybrid rendering, zoneless, signals | 📗 *Angular* + 🆕 SSR | [03] |
| UI | **Tailwind CSS v4 + spartan/ui** (headless + copied components), own design tokens | 📗 *Tailwind CSS* / 🆕 design system | You own every pixel. [03] |
| i18n | **Transloco**, `es-CO` default, `en` later; `LOCALE_ID`, `DEFAULT_CURRENCY_CODE=COP` | 🆕 | [03] |
| Frontend tests | **Vitest** unit, **Playwright** e2e, Storybook for components | ⚖️ Selenium → Playwright | [03] |
| Hosting (MVP) | VPS in Miami/NYC + **Dokploy** + Docker Compose, **DO Managed Postgres**, **Cloudflare** free | 📗 *Despliegue de sitios web* / 🆕 | ≈ US$45–60/month. [05] |
| CI/CD | GitHub Actions: build, Testcontainers, lint, CodeQL/Semgrep, Trivy, SBOM, cosign, k6 smoke, environments with approval | 📗 *Flujo de trabajo con Git y GitHub* / 🆕 supply chain | [05] |
| Observability | OpenTelemetry → **Grafana Cloud** (metrics, logs, traces), **Sentry**, **Better Stack** uptime | 🟡 *Monitoreo de recursos del servidor* | [05] |
| IaC | **OpenTofu** for Cloudflare, VPS, R2 | 🆕 | [05] |

Numbers in brackets are the research reports: [01] payments/legal, [02] shipping, [03] catalog/frontend, [04] backend/security, [05] infra, [06] AI, [07] customers/notifications, [08] repair domain.

### 4.4 Cross-cutting rules (write each as an ADR in `docs/adr/`)
1. **ADR-001 Modular monolith** with Spring Modulith; module dependencies declared, cycles forbidden.
2. **ADR-002 Money**: `Money(amountMinor, currency)` value object, `NUMERIC(19,2)` + `CHAR(3)`, `HALF_EVEN`, prices tax-inclusive, COP shown without decimals; per-provider minor-unit codec.
3. **ADR-003 Time**: `TIMESTAMPTZ`/`Instant`, UTC everywhere, `America/Bogota` only for display and daily cut-offs.
4. **ADR-004 Ids**: `BIGINT IDENTITY` internal, **UUIDv7** public, human order and ticket numbers from sequences.
5. **ADR-005 Errors**: RFC 9457 everywhere, error catalog with stable `type` URIs.
6. **ADR-006 Idempotency**: `Idempotency-Key` on every money-moving POST and every webhook; stored in PostgreSQL 24 h.
7. **ADR-007 State machines**: enum + explicit transition table per aggregate, every transition emits an event; no Spring Statemachine (commercial since 2025).
8. **ADR-008 Sessions over JWT** (see §3.3).
9. **ADR-009 Facts before AI**: models never query the database; they receive computed facts and every output is labeled and reviewable.
10. **ADR-010 Provider ports**: payments, carriers, invoicing, email, WhatsApp, AI, search and storage are interfaces with a Colombian adapter and a fake adapter for tests.

---

## 5. Domain model by module

### 5.1 Catalog
**Model** (commercetools-style): `Product` (abstract parent, type, taxonomy, SEO) → `Variant` (SKU, GTIN/MPN, option values, prices per currency, weight/dimensions, images) → `Offer` per warehouse. Typed attributes per `ProductType` (enum, number, text, boolean, reference).
- Product types: `PHYSICAL`, `DIGITAL` (keys/gift cards with encrypted one-time codes and region), `PART` (compatibility list), `SERVICE` (repair with diagnostic fee, lead time, `requiresDeviceIntake`), `BUNDLE` (component SKUs and quantities).
- Condition on the variant: `NEW`, `OPEN_BOX`, `REFURB_A/B/C`, `USED_AS_IS`; cosmetic grade only, function always tested; `ConditionReport` per serialized unit.
- `ConsoleModel` entity (brand, family, generation, model number such as CFI-1215A) and `Compatibility(partVariant ↔ consoleModel)`; the "find your model" gate in the storefront reads it.
- Taxonomy: Consoles › PlayStation / Xbox / Nintendo / Retro · Controllers · Accessories · Games (physical, digital) · Spare parts (by model) · Repair services · Refurbished · Merch; each category mapped to `google_product_category`.
- Pricing: `list`, `sale` with validity, `compare_at`, `cost` (internal); COP tax-inclusive; tax class per variant (19 %, 5 %, 0 %, excluido).
- Media: originals in R2, renditions via imgproxy, presigned uploads, magic-byte validation, required alt text.
- Search: `tsvector` generated column (`unaccent` + `spanish_stem`), `pg_trgm` for typos and part numbers, facets from indexed attributes; search documents rebuilt from `ProductChanged` through the outbox; Meilisearch adapter later behind a `SearchIndex` port.
- Import: keep the async CSV job; add supplier-feed mapping and image URLs.

**Concepts**: 📗 *Entidades JPA*, *Relacion muchos a muchos en JPA*, *Tabla intermedia como entidad en JPA*, *Consultas derivadas en Spring Data JPA* · 🆕 product/variant modeling, GTIN/MPN, FTS with `tsvector`, trigram indexes, object storage, presigned URLs, image pipeline, outbox-driven indexing.

### 5.2 Inventory
- `StockItem(variant, warehouse, onHand, reserved, version)` + `StockMovement` ledger with `warehouseId`; one default warehouse now, allocation strategy later.
- Serialized units (`ProductUnit` with serial, condition report, cost) for consoles and controllers; anonymous quantities for parts and accessories.
- Reservations stay atomic (conditional `UPDATE`); TTL depends on the payment method (§5.4); repairs reserve parts on quote approval and consume on QA pass.
- Reorder points, purchase orders (`PurchaseOrder`, `Supplier`), receiving with cost, low-stock events to the AI module.
- KPIs: inventory accuracy, days of cover, stockouts.

**Concepts**: 📗 *Bloqueo optimista y pesimista en JPA*, *JPQL y Query*, *Transactional en Spring* · 🆕 multi-warehouse allocation, serialized inventory, purchase orders.

### 5.3 Ordering
- `Cart` (server-side for logged users, local for guests, merged at login), `Order`, `OrderLine` (snapshot of SKU, name, price, tax, discount), `OrderAddress`, `AppliedPromotion`.
- State machine: `CREATED → PENDING_PAYMENT → PAID → PROCESSING → (PARTIALLY_SHIPPED) → SHIPPED → DELIVERED → COMPLETED`, plus `EXPIRED`, `CANCELLED`, `RETURN_OPEN → REFUNDED/EXCHANGED`, `BACKORDERED`, and `COD_CONFIRMATION_PENDING` for cash on delivery. Fulfillment status is derived from shipments.
- Checkout: guest or account, structured Colombian address (DIVIPOLA municipality, via/number nomenclature, rural landmark), shipping quote, payment method, legal summary step with express acceptance (Ley 1480 art. 50, Ley 527), `Idempotency-Key` on `POST /orders`.
- Mixed carts: products and services in one order; service lines create repair tickets on payment.
- Retracto: `RETURN_OPEN` with reason `RETRACTO` inside 5 business days after delivery; automatic `COMPLETED` after the window.

**Concepts**: 📗 *Codigos de estado HTTP*, *DTOs en Spring Boot* · 🆕 order state machines, idempotency keys, guest checkout, address normalization.

### 5.4 Payments
- Aggregates: `Payment` (one per order, canonical state), `PaymentAttempt` (one per provider transaction), `ProviderEvent` (raw webhook, unique per provider+event id), `Refund`, `Dispute`.
- States: `CREATED → PENDING → APPROVED | DECLINED | EXPIRED | VOIDED`; `APPROVED → PARTIALLY_REFUNDED → REFUNDED`; `APPROVED → DISPUTE_OPEN → WON | LOST`.
- Flow: server creates the checkout with the **integrity signature** → hosted Wompi page → redirect renders only UI → **webhook** verified by checksum → stored → 200 → processed from the outbox → **re-fetched from the provider API** → amount/currency/reference compared → order `PAID` → events.
- Reservation TTL by method: cards/Nequi 10–30 min, PSE until provider expiry, cash references 1–3 days; late approval after release re-reserves or auto-refunds.
- Reconciliation job: pending attempts past their window, daily settlement report vs approved payments, withholdings (renta, reteIVA, reteICA) recorded for the accountant.
- Refunds by line; each refund produces a credit note; `Dispute(reason=REVERSION)` models Ley 1480 art. 51.
- PCI: SAQ A only (hosted checkout, never a card field on our pages).

**Concepts**: 🆕 payment gateways, webhooks and signatures, PCI DSS SAQ A, reconciliation, chargebacks, reversión del pago, retenciones. Base notes: *API REST*, *Peticiones y respuestas HTTP*, *Headers HTTP*.

### 5.5 Invoicing and tax
- `TaxCalculator` port with `ColombiaIvaCalculator` (rate by tax class) and room for Stripe Tax/Avalara later; tax lines stored per order line.
- `InvoicingProvider` port with a Factus adapter: factura electrónica on `PaymentApproved` (buyer name + cédula/NIT captured at checkout), nota crédito on refund, PDF+XML stored in R2 and emailed; retries through the job queue; POS document only for sales ≤ 5 UVT (walk-in accessories).
- Régimen (RST or ordinary) and withholding handling decided with the accountant before launch.

**Concepts**: 🆕 DIAN electronic invoicing, CUFE, UVT thresholds, IVA classes, RST.

### 5.6 Shipping and returns
- `CarrierGateway` port (quote, createShipment, label, track, schedulePickup, cancel, parseWebhook) with Envia.com and Aveonline adapters and a `CarrierRouter` (COD?, rural?, weight, price, promise).
- `Shipment` state machine `DRAFT → QUOTED → LABEL_CREATED → PICKUP_SCHEDULED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`, with `DELIVERY_FAILED`, `RETURNED_TO_SENDER`, `EXCEPTION/CLAIM`; raw carrier events kept.
- Address model for Colombia (DIVIPOLA codes, via nomenclature, rural fields, geo) and a generic international address; municipalities seeded from the DANE dataset.
- Checkout shows cheapest / fastest / pickup point / in-store pickup; promise = carrier days + 1 handling day; tracking page reads normalized events.
- COD controls: eligibility by city and amount, WhatsApp confirmation before allocation, blocklist, settlement reconciliation.
- Returns: `Return` (RMA) with reasons `RETRACTO`, `WARRANTY`, `DOA`, `WRONG_ITEM`, inspection, disposition (`RESTOCK`, `REFURBISH`, `SCRAP`, `RTV`), refund or exchange.
- Fulfillment: pick list, packing slip with QR, barcode scan at pack, packaging rules for consoles, insurance by declared value.

**Concepts**: 🆕 carrier aggregators, guías, DIVIPOLA, volumetric weight, RMA, chain of custody, incoterms (later). Base notes: *API REST*, *Serializacion JSON con Jackson*.

### 5.7 Repairs (the workshop)
- Entities: `Device` (model number, serial), `RepairTicket` (channel walk-in/drop-off/mail-in/home pickup, tier, intake checklist, photos, encrypted credentials, terms and data-loss disclaimer, technician, promised date), `StatusHistory`, `Diagnosis`, `Quote` + `QuoteLine` (versioned, immutable once approved, expiry), `PartUsage` (reserve on approval, consume on QA), `LaborEntry`, `Technician`, `QAChecklist`, `RepairWarranty` + `WarrantyClaim`, `Appointment`, `Loaner`.
- State machine: `REQUESTED → CHECKED_IN → DIAGNOSING → QUOTED → APPROVED → (WAITING_PARTS) → IN_REPAIR → QA → READY → PICKED_UP | SHIPPED → DELIVERED → CLOSED`, with `DECLINED`, `EXPIRED`, `UNREPAIRABLE`, `CANCELLED`, `NOTICE_SENT → ABANDONED`, and `WARRANTY_CLAIM` tickets.
- Legal rules built in: Art. 18 intake receipt (PDF + notification), express acceptance of price and date, 3-month repair warranty unless waived in writing, 30/60-business-day clock for warranty repairs, abandonment notices (1 + 2 months), custody log.
- Operations: diagnostic fee waived on approval, stale-ticket alerts, public tracking page by ticket number + surname, technician workbench in the back office, KPIs (first-time-fix rate, resolution time, utilization, approval rate).
- Mail-in: inbound label through the shipping module, packaging instructions, tamper seal, insured return.

**Concepts**: 🆕 repair ticket lifecycle, quotes and approvals, parts consumption, warranty claims, Ley 1480 art. 8/18, Decreto 735/2013. Base notes: *Relacion uno a muchos en JPA*, *Eliminacion logica*, *Auditoria e historial de cambios en JPA*.

### 5.8 Customers and identity
- `Customer` (person or company with NIT), `Address` book, `Consent` ledger (terms, privacy, marketing email/WhatsApp/SMS, cookies; version, timestamp, IP, evidence), `DataRightsRequest` (export, rectify, delete → anonymize).
- Registration with email verification, OWASP-style password reset, guest → account conversion, Google sign-in, later magic links and passkeys.
- Staff: `ADMIN`, `OPERATOR` (warehouse), `TECHNICIAN`, `SUPPORT`; TOTP mandatory, passkeys optional, separate admin host.
- Password policy per NIST 800-63B-4: 15+ characters, no composition rules, breached-password check (HIBP k-anonymity), per-account throttling.

**Concepts**: 📗 *Usuarios y roles en Spring Security*, *UserDetailsService en Spring Security*, *Cifrado de contrasenas con BCrypt* · 🆕 OAuth2 login, TOTP, WebAuthn, consent ledger, data-subject rights.

### 5.9 Promotions, gift cards, loyalty
- `Promotion` (percent, fixed, free shipping, BOGO, threshold gift; scope; conditions; limits; stacking rules; priority), `CouponCode`, `PromotionRedemption` inside the order transaction.
- Gift cards are a payment method with a ledger; store credit ledger for trade-ins; loyalty points earned on delivery, reversed on refund (later).

### 5.10 Notifications and support
- Event-driven pipeline: domain event → outbox → notification worker → preference check → versioned template (Thymeleaf/MJML, WhatsApp template names) → channel adapter → provider webhooks update status, suppress bounces.
- Transactional set at launch: order confirmation and acuse de recibo, payment received, shipped with tracking, delivered, refund, repair received/quoted/ready, password reset, email verification.
- Support: Chatwoot (WhatsApp, Instagram, email, web widget), PQR form with radicado and 15-business-day SLA timer, help center, order and ticket self-service pages.

**Concepts**: 📗 *Thymeleaf*, *Fragmentos en Thymeleaf* · 🆕 outbox, templates versioning, SPF/DKIM/DMARC, WhatsApp Cloud API windows and templates, suppression lists.

### 5.11 Content, SEO and legal pages
- `Page` with versions (terms, privacy policy and aviso, retracto and reversión policies, warranty, shipping, PQR, cookies), FAQ, blog/guides (repair guides are SEO gold), banners.
- Sitemaps, robots, canonical URLs, JSON-LD (`Product`, `Offer`, `ProductGroup`, `BreadcrumbList`, `Organization`, `LocalBusiness` for the workshop), Merchant Center feed.

### 5.12 Analytics
Read models refreshed by events or nightly: sales KPIs, inventory health, repair KPIs, marketing attribution (UTM), forecasts (moving average → Holt-Winters). Materialized views, never aggregation over live tables per request.

---

## 6. Frontend plan

### 6.1 Applications
- **Storefront** (`apps/storefront`): SSR hybrid (prerender home/categories/policies, server-render product and service pages, client-render cart/checkout/account), zoneless, signals, `@defer` for reviews and recommendations, PWA shell for offline browsing (never caches checkout).
- **Back office** (`apps/backoffice`, separate host `admin.`): dashboard, catalog editor with media and variants, inventory and purchasing, orders and fulfillment board, shipments and returns, **technician workbench** (ticket queue, intake form with camera, quote builder, QA checklist), customers and consents, promotions, content/legal pages, notifications templates, AI reports, audit log, settings (feature flags, providers).
- **Public tools**: order tracking, repair tracking, PQR form, repair booking and mail-in form, pre-diagnosis assistant.

### 6.2 Design system (why it will not look AI-made)
- Brand identity first: name, wordmark, one accent color, neutral surfaces, semantic colors for stock/condition/grade; typography pair (display + workhorse); 8 px rhythm, 12-column grid, one card ratio per context.
- Real photography on a consistent background with scale cues; honest condition photos for refurbished units; no stock renders.
- Tailwind v4 tokens (`@theme`) + spartan/ui headless components copied into the repo and styled once; extract repeated utility groups into components (the professor's "infierno de clases" warning), never `@apply` soup.
- Merchandising through state: "Shipping now", "Pre-order", "Grade B", "Compatible with your PS5 CFI-1215A"; grade selectors as buttons with explanations; total price, warranty and return policy next to the CTA.
- Micro-interactions with intent (add-to-cart drawer with live announcement, view transitions between list and detail), `prefers-reduced-motion` respected, designed empty/loading/error states, specific Spanish copy.
- References to study: analogue.co, teenage.engineering, 8bitdo.com, frame.work, ifixit.com, backmarket.com.

### 6.3 Quality bars
- WCAG 2.2 AA checklist (focus visible, 24×24 targets, no redundant entry in checkout, contrast 4.5:1, labelled inputs, `LiveAnnouncer`, focus management on navigation).
- Core Web Vitals p75: LCP ≤ 2.5 s, INP ≤ 200 ms, CLS ≤ 0.1; measured with `web-vitals` in production.
- i18n with Transloco (`es-CO` now, `en` later), `LOCALE_ID`, COP formatting without decimals, locale-prefixed routes when the second language arrives.
- Analytics: GA4 e-commerce events through GTM, Meta Pixel + Conversions API with `event_id`, consent banner feeding Consent Mode v2.
- Tests: Vitest for services, stores and pure logic; Playwright for the five golden journeys (browse → buy, guest checkout, repair booking, tracking, admin fulfillment); Storybook for the component library; ESLint + Prettier; bundle budgets.

**Concepts**: 📗 *Angular*, *Componentes en Angular*, *Enrutamiento en Angular*, *Formularios reactivos en Angular*, *Validacion de formularios en Angular*, *HttpClient en Angular*, *Interceptores HTTP en Angular*, *Tailwind CSS*, *Sass*, *CSS Grid*, *Flexbox*, *Media queries y diseno responsive*, *Meta etiquetas*, *Imagenes HTML*, *HTML semantico* · 🆕 SSR and hydration, render modes, structured data, sitemaps, Core Web Vitals, WCAG 2.2, design tokens, headless components, Transloco, Consent Mode, Vitest, Playwright, Storybook, PWA.

---

## 7. Security and compliance plan

### 7.1 Identity and sessions
Spring Session in Redis, cookie `__Host-SID; HttpOnly; Secure; SameSite=Lax` (Strict on admin), absolute and idle timeouts, session fixation protection, logout everywhere, `csrf.spa()` with Angular's XSRF interceptor. Password policy and throttling per §5.8; TOTP for staff; passkeys for admins; Google login for customers.

### 7.2 Application controls (OWASP Top 10:2025, API Top 10, ASVS 5.0 L2)
BOLA scoping on every customer query and `@PreAuthorize` on services · separate request/response DTOs, field allow-lists on admin updates · Bucket4j limits on login, reset, checkout, coupons; payload and pagination caps · `InetAddressFilter` on outbound HTTP clients (SSRF) · fail-closed payment states · security headers (HSTS, CSP without `unsafe-inline`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, COOP/CORP), `Cache-Control: no-store` on the API · admin on its own host with IP allow-list and MFA · secrets out of Git (`.env` mode 600 now, SOPS+age next), rotation calendar · logs without PII, with auth and admin events.

### 7.3 Supply chain and testing
Dependabot/Renovate, Trivy on filesystem and image, CycloneDX SBOM from Boot, cosign-signed images, CodeQL (public repo) or Semgrep, SonarQube Community 📗, ZAP baseline against staging weekly, gitleaks; Modulith `verify()` and ArchUnit in the test suite.

### 7.4 Payments
SAQ A through hosted checkout only; integrity signature server-side; webhook checksum verification; idempotent processing; status confirmed by API before `PAID`; evidence retained for disputes.

### 7.5 Colombian law in the product (each item is a ticket)
| Law | What the store must do | Where |
|---|---|---|
| Ley 1480/2011 (consumer) | Seller identity block; tax-inclusive COP prices with breakdown; product info and availability; delivery ≤ 30 days; order summary with express acceptance and durable record; **retracto** flow (5 business days, refund within the legal term: 30 days in the statute, 15 per Ley 2439/2024, verify); **reversión del pago** handling; **garantía legal** per SKU and per repair (1 year new, 3 months used/refurbished and repair services); repair **intake receipt** (art. 18) and abandonment notices; **PQR** channel with radicado and 15-business-day answer; age gate | Footer, PDP, checkout, account, repair module, content pages |
| Ley 527/1999 (e-commerce) | Acuse de recibo of every order; data messages as evidence | Order confirmation email, audit log |
| Ley 1581/2012 + Decreto 1377/2013 (habeas data) | Prior informed consent with purposes and proof; política de tratamiento and aviso de privacidad; rights channel (know, update, rectify, delete, revoke) answered in 10/15 business days; data export and anonymization endpoints; international transfer disclosure (US hosting); RNBD registration only above 100,000 UVT in assets (verify); breach notification | Consent ledger, privacy pages, `/me/export`, `DELETE /me` |
| Ley 2300/2023 (contact) | Marketing only with express consent, within allowed hours and frequency; transactional exempt | Notification preferences, send-window scheduler |
| DIAN (invoicing) | Factura electrónica for every order above 5 UVT, credit notes, buyer identification | Invoicing module |
| PCI DSS 4.0.1 | SAQ A; no card data ever | Payments module |
| AI transparency (EU AI Act art. 50 when serving the EU; Colombia bill pending) | Label AI-generated answers and drafts; human contact path | Assistant UI |

---

## 8. Platform: environments, delivery, operations

### 8.1 Environments
`local` (Docker Compose with Spring Boot's Compose support) → `staging` (same Compose on the production host or a small VPS, separate database, `staging.` subdomain) → `production`. Same image digest promoted between them; `SPRING_PROFILES_ACTIVE` selects configuration; secrets injected, never baked.

### 8.2 Hosting path
| Stage | Setup | ≈ USD/month |
|---|---|---|
| MVP (hundreds of orders/month) | 1 VPS 2 vCPU/4 GB in Miami or NYC (60–75 ms from Bogotá) with Dokploy; API ×2 + SSR + Redis in Compose; DO Managed Postgres 1 GB (PITR); Cloudflare free (DNS, CDN, WAF, TLS); R2 for media; SES/Resend free tier; Grafana Cloud + Sentry + Better Stack free tiers; `.co` + `.com` domains | 45–60 |
| Growth (thousands/month) | Second app host with Kamal/Dokploy multi-server, HA Postgres, managed Valkey, Cloudflare Pro, paid observability | 150–300 |
| Alternative growth | AWS us-east-1: ECS Fargate ×2, ALB, RDS t4g.small Multi-AZ, ElastiCache Serverless, OpenTofu | 150–250 |
| Kubernetes | Only with > 5 services or a second engineer | – |

Triggers to move: sustained CPU > 60 %, > 2 replicas needed at peaks, DB pressure, > 4 h/month of manual ops, or a partner demanding an SLA.

### 8.3 CI/CD pipeline
Pull request: build and unit tests → Testcontainers integration tests → lint/format (Spotless, ESLint, Prettier) → CodeQL/Semgrep → Dependabot + Trivy fs → Angular production build → Docker multi-stage build with layered jar and CDS/AOT cache → Trivy image scan → SBOM (Syft) → cosign keyless signature → push to GHCR by digest. `main`: deploy to staging automatically, k6 smoke, Playwright golden journeys against staging, manual approval (GitHub Environment) → production rolling deploy (`docker-rollout`/Dokploy) → post-deploy synthetic checkout. Flyway runs as a one-off step before the rollout with `spring.flyway.enabled=false` in replicas. Versioning with Conventional Commits + release-please; rollback = redeploy previous digest.

### 8.4 Observability and reliability (must-have before launch)
Structured JSON logs with trace/span ids → Grafana Cloud (Loki); Micrometer/OpenTelemetry metrics and traces (Prometheus, Tempo); RED dashboard per endpoint, USE per host, business panel (orders/hour, checkout conversion, payment failures, tickets opened); Sentry for backend and Angular; Better Stack uptime with status page and phone alerts; synthetic checkout every 15 minutes against a test SKU in the gateway sandbox; alerts: 5xx > 2 %, p95 > 1.5 s, DB connections > 80 %, disk > 80 %, cert < 14 d, backup failed, no orders in 3 business hours. Backups: managed PITR or pgBackRest to R2 with a **monthly restore drill**; targets RPO ≤ 15 min, RTO ≤ 2 h. Runbooks in `docs/runbooks/` (deploy, rollback, restore, rotate secrets, gateway outage, site down), blameless postmortems.

---

## 9. AI roadmap (grounded, priced, reviewable)

| Phase | Capability | Design | Guardrails |
|---|---|---|---|
| A (foundation) | Spring AI 2.0 behind the existing `AiClient` port; token/cost metrics per feature; feature flags; golden datasets + eval runner; PII pseudonymization; paid Gemini tier | Structured outputs with JSON schema, model routing small → large, batch API for offline work, prompt caching | Kill switch per feature, AI disclosure copy |
| B (MVP value) | Hybrid semantic search and "compatible part for my console" (pgvector HNSW + FTS, RRF); recommendations v1 (co-purchase SQL); listing assistant (descriptions, SEO text, alt text, attributes from photos) human-approved; ops automation (weekly reports kept, purchase-order suggestions, review moderation, order risk rules) | No LLM in the ranking path; compatibility from the table, never inferred; drafts only | Evals: recall@10, MRR; human rating of drafts |
| C (conversations) | Repair pre-diagnosis (symptoms → probable faults, parts, price range from the shop's table; technician confirms); support assistant with read-only tools (catalog, stock, order status by verified order+email) and RAG over policies with citations; escalation to a human | Tools least-privilege, identity from `ToolContext` never from the model, tool-call limits, output schema with `needs_human` | Injection red-team suite in CI, citation precision, escalation rate |
| D (scale) | WhatsApp channel, admin copilot (NL → SQL over read-only views), fraud scoring v2, demand forecasting (Holt-Winters before ML), grading from photos (pilot) | Read-only DB role, `LIMIT`, statement timeout, SQL shown to the admin | Backtests (MAPE, precision/recall) |

Models at research time: `gemini-3.8-flash` (default, multimodal), `gemini-3.1-flash-lite` (classification), `claude-sonnet-5` / `gemini-3.1-pro-preview` (reasoning), `gemini-embedding-2` or `text-embedding-3-small` (embeddings); re-verify ids and prices quarterly.

---

## 10. Delivery plan: phases, PRs, acceptance

Rules of the road (unchanged from the demo): one feature per PR, stacked and merged in order, Conventional Commits in English, no AI co-author lines, tests and docs in the same PR, an ADR whenever a §4.4 rule is touched. Effort is full-time-equivalent weeks for one developer; halve the pace for part-time.

### Milestones
- **M1 · Soft launch (phases 0–5 + minimum of 7 and 8)**: sell products and take repair bookings to a limited audience. ≈ 20 FTE weeks.
- **M2 · Full platform (phases 6–9)**: notifications at scale, redesigned storefront, observability hardened, AI features. ≈ 14 more FTE weeks.
- **M3 · Growth (phase 10)**: promotions v2, marketplaces, second language and currency.

### Phase 0 · Foundation and rename (2 weeks)
| PR | Scope | Acceptance |
|---|---|---|
| `chore/rename-game-store` | Repo, Java package `co.gamestore`, Angular app name, README, docker services, env vars | Everything builds; no "shopscale" string left |
| `chore/upgrade-boot-4-java-25` | Boot 4.1, Java 25, Jackson 3, `@MockitoBean`, virtual threads | All 27 tests green on Testcontainers |
| `feat/flyway-baseline` | `V1__baseline.sql` from the current schema, `ddl-auto=validate`, migration conventions doc | App starts clean on an empty PostgreSQL |
| `feat/modulith-skeleton` | Spring Modulith, module packages, `ApplicationModules.verify()` test, event registry tables | Module boundary test passes; docs diagram generated |
| `feat/shared-kernel-money-ids` | `Money`, UUIDv7 public ids, RFC 9457 error catalog, Problem Details advice, COP formatting | ADR-002/004/005 written |
| `chore/testcontainers-ci` | Testcontainers for PostgreSQL + Redis, CI job, Spotless, Trivy fs, Dependabot | CI green with the new gates |

Concepts: 📗 *Migraciones de esquema con Flyway*, *Perfiles de Spring Boot*, *ArchUnit*, *Pruebas de integracion con SpringBootTest* · 🆕 Spring Modulith, Testcontainers, UUIDv7, Problem Details catalog, Boot 4 migration.

### Phase 1 · Identity and security hardening (2 weeks)
| PR | Scope | Acceptance |
|---|---|---|
| `feat/session-cookies-csrf` | Spring Session Redis, cookie login/logout, `csrf.spa()`, Angular XSRF, remove JWT from `localStorage` | Session survives replica switch; CSRF test |
| `feat/password-policy-hibp` | NIST rules, HIBP k-anonymity check, per-account throttling, generic errors | Security tests |
| `feat/email-verification-reset` | Verified email, reset tokens (hashed, single use, 15 min), session invalidation | OWASP checklist test |
| `feat/staff-mfa-totp` | TOTP enrollment for staff, step-up on `/admin/**`, recovery codes | e2e login with TOTP |
| `feat/security-headers-hardening` | CSP, HSTS, `Referrer-Policy`, `Permissions-Policy`, `no-store`, Nginx `server_tokens off`, admin host with allow-list | Headers verified by a test |
| `feat/data-rights-consents` | Consent ledger, `/me/export`, `DELETE /me` anonymization, privacy pages v1 | Legal review checklist |

Concepts: 📗 *Spring Security*, *Cookies*, *CSRF*, *Cifrado de contrasenas con BCrypt*, *Pruebas de seguridad en Spring Boot* · 🆕 Spring Session, RFC 10017, NIST 800-63B-4, HIBP, TOTP, security headers, consent ledger.

### Phase 2 · Catalog v2, media and search (3 weeks)
| PR | Scope | Acceptance |
|---|---|---|
| `feat/catalog-variants-attributes` | Product types, variants, typed attributes, condition and grade, tax class, warranty fields, migrations with data backfill | Existing products migrated; API v1 versioned |
| `feat/console-models-compatibility` | `ConsoleModel`, compatibility matrix, "fits my model" filter | Seeded models for PS4/PS5/Xbox/Switch |
| `feat/media-r2-imgproxy` | Presigned uploads, validation, renditions, `NgOptimizedImage` loader, alt text required | Images served as WebP/AVIF from the CDN |
| `feat/search-postgres-fts` | `tsvector` + `unaccent` + `spanish`, `pg_trgm`, facets, synonyms table, outbox-driven index refresh | Golden query set recall@10 ≥ target |
| `feat/digital-keys-and-services` | Encrypted key vault, one-time reveal, service products with diagnostic fee | Keys never logged; reveal audited |
| `feat/supplier-import-v2` | Feed mapping, images by URL, dry run, error report | 5,000-row feed imports in background |

Concepts: 📗 *Relacion muchos a muchos en JPA*, *JPQL y Query*, *Rendimiento y escalabilidad con JPA* · 🆕 product modeling, FTS, trigram indexes, object storage, presigned URLs, image pipeline.

### Phase 3 · Checkout, payments, invoicing, legal pages (4 weeks)
| PR | Scope | Acceptance |
|---|---|---|
| `feat/checkout-v2-addresses` | Server cart, guest checkout, Colombian address model with DIVIPOLA seed, legal summary step with express acceptance and audit record | Playwright guest checkout |
| `feat/payments-module-wompi` | Payment aggregates and state machine, Wompi hosted checkout with integrity signature, webhook verification, outbox processing, API re-fetch, reservation TTL by method | Sandbox end-to-end: cards, PSE, Nequi, cash |
| `feat/payments-idempotency-reconciliation` | `Idempotency-Key` store, pending-attempt poller, settlement reconciliation, withholdings record | Duplicate POST returns the same order |
| `feat/refunds-disputes` | Refund by line, dispute model incl. reversión, evidence pack | Refund emits events |
| `feat/tax-engine-colombia` | `TaxCalculator` port, IVA classes, tax lines on orders | Totals match accountant's examples |
| `feat/invoicing-factus` | `InvoicingProvider` port, Factus adapter, invoice on payment, credit note on refund, PDF/XML in R2 and email | Sandbox invoice validated by DIAN test env |
| `feat/legal-pages-v1` | Terms, privacy, retracto, reversión, warranty, shipping, PQR pages with versions; seller identity footer; age gate | Checklist §7.5 reviewed |
| `feat/mercadopago-adapter` | Second gateway behind the port, feature-flagged | Same tests pass with both adapters |

Concepts: 🆕 gateways, webhooks, idempotency, reconciliation, PCI SAQ A, DIAN invoicing, IVA, Ley 1480/527 duties. Base notes: *API REST*, *Headers HTTP*, *Serializacion JSON con Jackson*, *Transactional en Spring*.

### Phase 4 · Shipping, fulfillment and returns (3 weeks)
| PR | Scope | Acceptance |
|---|---|---|
| `feat/shipping-carrier-port-envia` | `CarrierGateway`, Envia.com adapter (quote, label, tracking, pickup, webhook), rate cache | Sandbox label created from an order |
| `feat/shipping-checkout-options` | Quote at checkout, options (cheapest/fastest/pickup/in-store), promise date, COD eligibility and confirmation | e2e with three options |
| `feat/fulfillment-board` | Pick list, packing slip with QR, pack scan, partial shipments, backorders | Operator flow in back office |
| `feat/tracking-and-notifications-hook` | Normalized tracking events, public tracking page, events to notifications | Webhook replay is idempotent |
| `feat/returns-rma` | RMA state machine, reasons and legal paths, inspection, disposition, refund/exchange | Retracto and warranty flows tested |
| `feat/aveonline-adapter` | Fallback carrier adapter + router rules | Router picks by capability |

Concepts: 🆕 aggregators, DIVIPOLA, volumetric weight, RMA, COD controls. Base notes: *API REST*, *Peticiones y respuestas HTTP*.

### Phase 5 · Repair workshop (4 weeks)
| PR | Scope | Acceptance |
|---|---|---|
| `feat/repairs-domain-tickets` | Device, ticket, status history, state machine, intake checklist and photos, encrypted credentials, art. 18 receipt PDF | Receipt generated on check-in |
| `feat/repairs-diagnosis-quotes` | Diagnosis, versioned quotes, approval by portal/WhatsApp, expiry and reminders, diagnostic fee rules | Approval recorded with who/when/channel |
| `feat/repairs-parts-and-labor` | `PartUsage` reserve/consume/release via inventory, labor entries, technician assignment, QA checklist | Stock ledger shows repair consumption |
| `feat/repairs-warranty-and-abandonment` | Repair warranty, warranty claims, 30/60-day clocks, abandonment notices, custody log | Scheduled notices tested with a fake clock |
| `feat/repairs-booking-mailin` | Appointment slots, mail-in request with inbound label, packaging instructions, insured return | Customer flow e2e |
| `feat/technician-workbench` | Back-office queue, ticket view, quote builder, KPIs (FTFR, resolution time, utilization) | Usable on a tablet |
| `feat/repair-tracking-page` | Public status by ticket number + surname | Playwright |

Concepts: 🆕 repair lifecycle, quotes, warranties, abandonment law, custody, KPIs. Base notes: *Relacion uno a muchos en JPA*, *Auditoria e historial de cambios en JPA*, *Eliminacion logica*.

### Phase 6 · Notifications, support and growth tooling (3 weeks)
| PR | Scope | Acceptance |
|---|---|---|
| `feat/notifications-pipeline` | Outbox consumer, preferences, versioned templates, email adapter (SES), idempotent sends, provider webhooks, suppression | No duplicate sends on retry |
| `feat/email-templates-transactional` | MJML → Thymeleaf templates for the launch set, SPF/DKIM/DMARC docs | Rendered previews in Storybook-like gallery |
| `feat/whatsapp-cloud-api` | Templates, opt-in capture, 24-h window logic, status webhooks | Order shipped template delivered in sandbox |
| `feat/support-chatwoot-pqr` | Chatwoot deployment, web widget, click-to-chat with order context, PQR with radicado and SLA timer | PQR answered flow |
| `feat/reviews-and-wishlist` | Verified-purchase reviews with moderation, review requests after delivery, wishlist, back-in-stock alerts | AggregateRating JSON-LD |
| `feat/analytics-tags-consent` | GTM/GA4 e-commerce events, Meta Pixel + CAPI, consent banner + Consent Mode v2, Merchant Center feed | Events visible in GA4 DebugView |

Concepts: 📗 *Thymeleaf*, *Fragmentos en Thymeleaf* · 🆕 outbox consumers, deliverability, WhatsApp Cloud API, Consent Mode, Merchant Center.

### Phase 7 · Storefront redesign with SSR (5 weeks)
| PR | Scope | Acceptance |
|---|---|---|
| `chore/angular-21-tailwind-spartan` | Angular 21 LTS, Tailwind v4, spartan/ui, design tokens, ESLint/Prettier, Vitest, Playwright, Storybook | Component library with 20 base components |
| `feat/ssr-hybrid-rendering` | SSR server, render modes per route, hydration with event replay, `RESPONSE_INIT` 404/410, sitemaps, robots | Lighthouse SEO 100, prerendered pages cached at the edge |
| `feat/storefront-catalog-pages` | Home, category, PLP with facets, PDP with variants/condition/compatibility, JSON-LD, `NgOptimizedImage` | CWV targets on p75 lab runs |
| `feat/storefront-cart-checkout-ui` | Reactive-forms checkout, address autocomplete, shipping options, payment redirect, order confirmation | Golden journeys in Playwright |
| `feat/storefront-account-tracking` | Account, addresses, consents, orders, returns, repair tickets, tracking pages | a11y audit passes |
| `feat/repair-booking-ui` | Booking, mail-in, pre-diagnosis form | Playwright |
| `feat/backoffice-redesign` | Separate app on `admin.` host, reactive forms everywhere, technician workbench UI, fulfillment board | Operator and technician usability test |
| `feat/i18n-transloco-es-co` | Transloco scopes, `es-CO` locale, COP formatting, copy review | No hard-coded strings in templates |
| `feat/pwa-shell` | Service worker for assets only, manifest | Installable; checkout never cached |

Concepts: 📗 *Angular*, *Formularios reactivos en Angular*, *Tailwind CSS*, *Meta etiquetas* · 🆕 SSR, hydration, render modes, JSON-LD, CWV, WCAG 2.2, tokens, headless UI, Transloco, Vitest, Playwright, Storybook, PWA.

### Phase 8 · Production platform and launch (3 weeks)
| PR | Scope | Acceptance |
|---|---|---|
| `chore/infra-opentofu-cloudflare` | OpenTofu for DNS, cache and WAF rules, R2 buckets, VPS | `tofu plan` clean |
| `chore/deploy-dokploy-compose-prod` | Production Compose (API ×2, SSR, Redis, imgproxy, Chatwoot), Caddy/Nginx with Cloudflare origin cert, container hardening, `docker-rollout` | Zero-downtime deploy demonstrated |
| `chore/ci-supply-chain` | CodeQL/Semgrep, Trivy image, SBOM, cosign, k6 smoke, Playwright on staging, environments with approval, OIDC | Signed image verified on the host |
| `chore/observability-stack` | JSON logs with trace ids, OTel to Grafana Cloud, dashboards, Sentry, Better Stack, alert rules, synthetic checkout | Alerts fire in a game day |
| `chore/backups-runbooks` | Managed PITR or pgBackRest to R2, restore drill recorded, runbooks, postmortem template | Restore in < 2 h documented |
| `chore/launch-checklist` | §11 verified item by item, legal review, accountant sign-off | Go/no-go recorded |

Concepts: 📗 *Despliegue de sitios web*, *Monitoreo de recursos del servidor*, *Pruebas de carga con k6* · 🆕 IaC, supply-chain security, OpenTelemetry, SLOs, backups/DR, runbooks.

### Phase 9 · AI features (4+ weeks, after M1)
`feat/spring-ai-foundation` (port over ChatClient, metrics, flags, evals, pseudonymization) → `feat/search-hybrid-pgvector` → `feat/recommendations-v1` → `feat/listing-assistant` → `feat/ops-automation-po-moderation` → `feat/repair-prediagnosis` → `feat/support-assistant-rag` → `feat/admin-copilot-sql` (read-only). Each PR ships its golden dataset and eval in CI.

### Phase 10 · Growth (ongoing)
Promotions v2 (BOGO, thresholds, stacking), gift cards and store credit, trade-in program, pre-orders and bundles, Mercado Libre sync, Meilisearch adapter, second language and currency, Addi BNPL, loyalty and referrals, marketplace feeds, WhatsApp campaigns through a BSP, A/B testing (GrowthBook).

---

## 11. Launch checklist (M1 gate)

**Legal and fiscal**: seller identity block · terms, privacy policy and aviso, retracto, reversión, warranty, shipping and PQR pages published and versioned · consent checkboxes unticked by default with proof stored · age gate · accountant confirmed régimen, IVA classes and withholdings · Factus production credentials · DIAN test invoices validated · RNBD decision documented.
**Payments**: Wompi production keys in secrets store · integrity signature server-side · webhook checksum verified · idempotency on orders and webhooks · order paid only after API confirmation · refund path tested · reconciliation job running · fraud rules on high-value first orders.
**Fulfillment**: carrier account and negotiated rates · label and tracking tested end to end · packaging kit and insurance rule · COD eligibility rules · returns policy live.
**Repairs**: art. 18 receipt template · warranty text · intake and QA checklists · abandonment job.
**Security**: cookie sessions and CSRF · password policy · staff MFA · admin host allow-listed · headers audited · secrets rotated · Trivy and Dependabot clean · ZAP baseline reviewed · BOLA tests for orders, tickets, addresses.
**Reliability**: backups with a restore drill in the last 30 days · alerts routed to a phone · status page · synthetic checkout green · runbooks written · cost alerts.
**Quality**: backend integration suite on Testcontainers · Playwright golden journeys · k6 at 3× expected peak with 0 % 5xx · Lighthouse a11y ≥ 95 and CWV in range on PDP and checkout.
**Content**: photography set for launch SKUs · policies in specific Spanish · Merchant Center feed approved · Google Business Profile claimed.

---

## 12. Risks and open decisions

| Risk | Mitigation |
|---|---|
| Gateway onboarding delays (KYC, NIT, bank) | Start Wompi and Mercado Pago applications in Phase 0; build on sandbox meanwhile |
| Legal misreads (retracto term, RNBD threshold, warranty texts) | Items are flagged "verify" in the research; get a one-time review from a lawyer and the accountant before M1 |
| One developer, long plan | Milestone M1 cut, stacked small PRs, feature flags, fake adapters to keep every phase demoable |
| Version churn (Boot 4, Angular 21/22, model ids) | LTS only, pinned versions, quarterly review of model ids and prices |
| Design quality | Brand and photography decided early (Phase 0–2), reference sites, usability tests with real customers before M1 |
| AI cost or misbehavior | Paid tier, budgets and kill switches, facts-before-AI, human review, evals in CI |

**Decisions needed from Andres**: 1) brand name and domains (§1.4); 2) hosting provider for MVP (DigitalOcean vs Vultr) and who pays the ≈ US$50/month; 3) accountant contact for RST/IVA/invoicing; 4) gateway accounts (Wompi, Mercado Pago) and Factus account; 5) paid Gemini key for the AI phases; 6) whether the back office lives on `admin.<domain>` from day one (recommended).

---

## 13. Study map (🆕 concepts by phase)

| Phase | Study before building | Where |
|---|---|---|
| 0 | Spring Modulith, Testcontainers, Flyway expand/contract, UUIDv7, RFC 9457, Boot 4 migration | [04] glossary |
| 1 | RFC 10017 (BFF/cookies), Spring Session, CSRF double-submit, NIST 800-63B-4, HIBP, TOTP/WebAuthn, security headers, Ley 1581 consents | [04], [07] |
| 2 | Product/variant modeling, GTIN/MPN, PostgreSQL FTS and `pg_trgm`, object storage and presigned URLs, image renditions | [03] |
| 3 | Payment gateways and webhooks, idempotency keys, PCI SAQ A, reconciliation, DIAN invoicing, IVA/UVT, Ley 1480 art. 47/50/51, Ley 527 | [01] |
| 4 | Carrier aggregators, DIVIPOLA and Colombian addresses, volumetric weight, RMA, COD | [02] |
| 5 | Repair ticket lifecycle, quotes, part consumption, warranty law (art. 8/18, Decreto 735), abandonment | [08] |
| 6 | Transactional outbox consumers, email deliverability (SPF/DKIM/DMARC), WhatsApp Cloud API, Consent Mode v2, GA4 events | [07] |
| 7 | SSR and hydration, render modes, JSON-LD, sitemaps, Core Web Vitals, WCAG 2.2, design tokens, spartan/ui, Transloco, Vitest, Playwright, Storybook | [03] |
| 8 | OpenTofu, Dokploy/Kamal, container hardening, OpenTelemetry, SLOs, pgBackRest/PITR, runbooks, supply-chain security (SBOM, cosign) | [05] |
| 9 | Spring AI 2, embeddings and pgvector, hybrid search and RRF, RAG, tool calling, evals, prompt injection defenses, cost control | [06] |

Every report ends with a glossary written for exactly this purpose.
