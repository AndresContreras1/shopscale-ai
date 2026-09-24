# Research: backend architecture, data and security for a production e-commerce

> Research notes, September 2026. Items marked **verify** could not be confirmed on an official page.

## A. Architecture decisions

| Decision | Recommendation | Rationale / when to revisit |
|---|---|---|
| Monolith vs microservices | **Stay a modular monolith**, formalize it with **Spring Modulith 2.1.x** (GA June 2026, Boot 4 baseline) | One developer, one DB, one deploy. Microservices only pay off with several teams or independent scaling. Revisit when a module needs its own release cadence or team. |
| Module layout | One Modulith application module per bounded context: `catalog`, `inventory`, `ordering`, `payments`, `shipping`, `customers`, `repairs`, `promotions`, `notifications`, `analytics` (read model), plus a small shared kernel (`Money`, ids, `Problem` types). Modules = direct sub-packages of the `@SpringBootApplication` package; public types in the base package are the API, sub-packages are internal; expose extra packages with `@NamedInterface`; restrict with `@ApplicationModule(allowedDependencies=…)` | Boundaries are verified by `ApplicationModules.of(App.class).verify()` (ArchUnit underneath: no cycles, no internal access). Documenter generates C4/PlantUML diagrams. |
| Hexagonal-lite | Each external provider behind a port interface in the module API (`PaymentGateway`, `Carrier`, `EmailSender`, `LlmClient`) with adapters in `internal.<provider>` and a mock adapter for dev/tests (already done for AI) | Swap Wompi/PayU/carriers without touching domain code. |
| Domain events | Publish plain records via `ApplicationEventPublisher`; consume with `@ApplicationModuleListener` (async + `REQUIRES_NEW` + after-commit). Turn on the **Event Publication Registry** (`spring-modulith-starter-jpa`): publications are persisted in the same transaction, states PUBLISHED/PROCESSING/COMPLETED/FAILED/RESUBMITTED, `completion-mode=ARCHIVE` or `DELETE`, staleness monitor (`spring.modulith.events.staleness.*`), `republish-outstanding-events-on-restart=true` | This *is* a transactional outbox inside the monolith (no broker). With 2 replicas make listeners idempotent (verify duplicate handling on restart with multiple instances). |
| Broker / outbox to the outside | **No broker now.** When you must externalize (ERP, marketing), use Modulith's `@Externalized` with `spring.modulith.events.externalization.mode=outbox` (Namastack or JobRunr starter, 2.1) → RabbitMQ (simpler than Kafka for one person). Debezium CDC only if Kafka ever appears | Kafka + Connect is an ops detour for a single dev. |
| Background jobs & schedulers | Keep `@Scheduled` + **ShedLock 7.10** (`lockAtMostFor`) for the order-expiry job across the 2 replicas today. For retried work (emails, webhook delivery, CSV import) adopt a **Postgres-backed queue**: **db-scheduler 16.x** (Apache-2.0, one table, `db-scheduler-spring-boot-4-starter`) or **JobRunr** (LGPL, dashboard, retries). Skip Quartz (11 tables) | ShedLock is "not and will never be" a scheduler (its own README). |
| Idempotency | `Idempotency-Key` header on `POST /orders`, `POST /checkout`, refunds and every webhook. Store key + user scope + request fingerprint (SHA-256 of body) + response; replay stored response on match, **422** on fingerprint mismatch, **409** while a first request is in flight; TTL ~24 h in Postgres (not Redis, so it survives cache flushes) | IETF draft-ietf-httpapi-idempotency-key-header-07 (Oct 2025) is still an Internet-Draft (verify); treat it as an industry convention. |
| API conventions | URL versioning `/api/v1/**` (Spring Framework 7 also ships native API versioning, verify before using); **springdoc-openapi 3.x** (3.1.x for Boot 4) code-first, generate the Angular client with openapi-generator; **RFC 9457 Problem Details** via `spring.mvc.problemdetails.enabled=true` + one `@ControllerAdvice extends ResponseEntityExceptionHandler` (note: `BasicErrorController` still emits the legacy format, Boot issue #48392, so handle everything in the advice); pagination: offset `Pageable` with a hard `size` cap for admin, keyset/`ScrollPosition` for ledgers and order history; filters and sort fields via an allow-list | Consistent errors and pagination are cheap now, painful later. |
| CQRS-lite | `analytics` reads only from dedicated read tables / materialized views refreshed by event listeners or a scheduled `REFRESH MATERIALIZED VIEW CONCURRENTLY`; never aggregate over live order tables per request | Read replicas come much later (see C). |
| Spring Boot version | **3.5.16 is already past OSS EOL (2026-06-30, 3.5.16 was the last free patch).** Move to **Boot 4.1.x** (4.1.1, Aug 2026; OSS until 2027-07-31). Migration checklist: Java 17+ baseline, Jakarta EE 11/Servlet 6.1 (Tomcat 11), Spring Framework 7 / Security 7.1 / Hibernate 7.4 / Flyway 12.4, **Jackson 3** (`tools.jackson`, renamed customizers, properties under `spring.jackson.json.read/write`), modular starters (`spring-boot-starter-flyway`), `@MockitoBean` replaces `@MockBean`, `@SpringBootTest` needs explicit `@AutoConfigureMockMvc`, Undertow removed; use `spring-boot-properties-migrator` temporarily; 4.1 adds `InetAddressFilter` SSRF mitigation for HTTP clients (use it for the AI and webhook clients) | 4.0.x OSS ends 2026-12-31, so go straight to 4.1. |
| Java | **Yes, move now, to Java 25 LTS** (Boot 4.1 supports 17–26). Enable `spring.threads.virtual.enabled=true` (Tomcat + `applicationTaskExecutor` on virtual threads). Java 21 works but lacks JEP 491 (pinning fix, JDK 24+) | With virtual threads the Hikari pool becomes the real concurrency limit; keep it small and set timeouts. |

## B. Security decisions

**Identity provider:** keep Spring Security in-app. Keycloak 26.7 is a second stateful service to patch and back up; Spring Authorization Server standalone is archived and now lives inside Spring Security 7 (`spring-security-oauth2-authorization-server:7.x`), relevant only when third-party clients, a mobile app or SSO appear; Auth0/Clerk/Supabase add cost and lock-in. Revisit at "international + social login + many admin users".

**Token strategy (highest-impact change):** replace the 2 h HS256 JWT in `localStorage`. RFC 10017 (BCP 212, 2026, "OAuth 2.0 for Browser-Based Applications") and the OWASP HTML5 cheat sheet ("do not store session identifiers in local storage") both point the same way: the browser should hold only an **HttpOnly cookie**. Because Nginx already serves SPA and API on one origin, the simplest BFF-equivalent is **server sessions in Redis via Spring Session 4.1** (`spring-session-data-redis`): JSON login endpoint → session cookie `__Host-SID; HttpOnly; Secure; SameSite=Lax` (Strict on the admin host), absolute + idle timeouts, session fixation protection (default), logout = instant revocation, "log out everywhere" = delete the user's sessions. CSRF becomes mandatory: Spring Security 7's `csrf.spa()` (cookie `XSRF-TOKEN` + `X-XSRF-TOKEN`, BREACH-safe handler, refresh after login/logout) matches Angular `HttpClient`'s built-in XSRF interceptor; the cookie must not be HttpOnly. If JWTs are kept: 5–15 min access token in memory only, refresh token in an HttpOnly cookie with rotation + reuse detection, server-side denylist for revocation, RS256/ES256 or a ≥256-bit random HS256 key from the secret store.

**Passwords (NIST SP 800-63B-4, final July 2025):** minimum **15 chars** when password is the only factor (8 with MFA), accept at least 64, **no composition rules**, **no periodic rotation** (force change only on compromise evidence), blocklist check against breached passwords (HIBP Pwned Passwords range API: SHA-1 prefix of 5 chars, k-anonymity, free, no key), salted memory-hard/bcrypt hashing (BCrypt is fine; consider Argon2id via `DelegatingPasswordEncoder`), rate-limit failed attempts. Throttle **per account** (exponential backoff, OWASP Authentication cheat sheet) plus per IP; generic "invalid user or password" messages; never lock an account from the forgot-password flow (DoS vector).

**MFA:** TOTP mandatory for ADMIN and OPERATOR; add **passkeys** via Spring Security `webAuthn()` (`rpId`, `allowedOrigins`, `spring-security-webauthn`, JDBC `PublicKeyCredentialUserEntityRepository`/`UserCredentialRepository`; the default is in-memory). Spring Security 7.1 adds `AuthorizationManagerFactories.multiFactor()` and `MultiFactorCondition.WEBAUTHN_REGISTERED` for step-up on `/admin/**`.

**Email verification / reset (OWASP Forgot Password cheat sheet):** 256-bit CSPRNG token, store only its hash, single-use, 15–60 min expiry, identical response and timing whether the account exists, HTTPS link without trusting the `Host` header, `Referrer-Policy: no-referrer` on the reset page, invalidate sessions after reset, notify by email, rate-limit.

**OWASP mapping (Top 10:2025: A01 Broken Access Control now includes SSRF; A03 Software Supply Chain Failures and A10 Mishandling of Exceptional Conditions are new; API Top 10 2023; ASVS 5.0, May 2025, chapters V1–V17):**
- BOLA/IDOR (A01, API1): every customer-facing query is scoped by the authenticated customer id (`findByIdAndCustomerId`), public ids are UUIDv7 not sequences, `@PreAuthorize` on services, method security tests.
- BOPLA (API3): separate request/response DTOs, never bind entities; explicit field allow-lists for admin updates.
- Resource consumption (API4) and business-flow abuse (API6): Bucket4j per user/IP/endpoint (login, reset, checkout, coupon), payload size limits, pagination caps, reservation TTLs, coupon usage counters.
- SSRF: `InetAddressFilter` on `RestClient` for AI providers and outbound webhooks; no user-supplied URLs.
- A10: fail closed on gateway errors: an unknown payment state is never "paid".
- ASVS L2 chapters to self-assess: V2 validation/business logic, V3 frontend, V4 API, V6 authentication, V7 sessions, V8 authorization, V9 tokens, V11 crypto, V13 configuration, V14 data protection, V16 logging.

**Headers (OWASP Secure Headers):** `Strict-Transport-Security: max-age=63072000; includeSubDomains`, CSP for the SPA (`default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'` + hashes/nonces; Angular needs no `unsafe-inline` if you avoid inline styles), `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` (or `strict-origin-when-cross-origin` for the store), `Permissions-Policy`, COOP/CORP `same-origin`, `Cache-Control: no-store` on API responses; `server_tokens off` and strip `X-Powered-By` in Nginx.

**Secrets:** now: Docker secrets/`.env` outside Git; next: **SOPS + age** to keep encrypted config in Git (minutes to set up, no server); Vault only with a platform team; cloud secret manager when you move to a cloud. Rotate DB/Redis/HMAC/API keys; nothing in images.

**Admin hardening:** separate hostname (`admin.`) with its own Angular build and `SecurityFilterChain`, `SameSite=Strict`, MFA required, IP allow-list at Nginx/Cloudflare, shorter session TTL, audit log on every admin mutation.

**Payments / PCI DSS 4.0.1:** never touch PAN. Use a **hosted redirect checkout** (e.g., Wompi Web Checkout; PayU/Mercado Pago/ePayco similar, verify) → SAQ A, and the *redirect* method is exempt from the script-protection requirements that iframes need (since 2025-03-31). Compute the checkout **integrity signature** server-side (Wompi: SHA-256 of reference + amount_in_cents + currency [+ expiration] + integrity secret; never in the frontend), verify webhook checksums (`X-Event-Checksum` = SHA-256 of `signature.properties` values + timestamp + event secret), respond 200 immediately and process asynchronously (retries at 30 min / 3 h / 24 h), treat webhooks as idempotent, and confirm status with a GET on the transaction before marking an order paid.

**Privacy (Ley 1581/2012 + Decreto 1377/2013, authority: SIC):** prior, express, informed consent (privacy policy + unticked checkbox, keep proof), purpose limitation, rights to know/update/rectify/delete/revoke → implement `GET /me/export` (JSON) and `DELETE /me` that **anonymizes** the customer while keeping orders for tax/accounting; register in the RNBD if assets exceed the threshold (Decreto 090/2018, verify amount); breach notification duties to SIC (verify timeline). Logging: never log passwords, session ids, tokens, card data or PII (mask emails); do log auth success/failure, authz failures, validation failures, admin actions (OWASP Logging cheat sheet), with retention limits. Incident basics: written runbook (who, how to revoke sessions/secrets, preserve logs, notify SIC/customers, post-mortem), tested once.

**CI tooling:** Dependabot or Renovate; **Trivy** (`fs` + image scan, no NVD key) or Grype (OWASP Dependency-Check now needs an NVD API key and is slow); CycloneDX SBOM via Boot's built-in support (`cyclonedx-maven-plugin`, exposed at `/actuator/sbom`); SAST: CodeQL (free for public repos, verify for private) or Semgrep OSS, SonarQube Community for quality; DAST: ZAP baseline scan against staging weekly; secret scanning (gitleaks). Cloudflare free tier in front of Nginx: Bot Fight Mode, 5 custom WAF rules, 1 rate-limiting rule; restrict origin to Cloudflare IPs.

## C. Data decisions

- **Migrations:** Flyway 12.4 (managed by Boot 4.1; `spring-boot-starter-flyway` + `flyway-database-postgresql`), `spring.jpa.hibernate.ddl-auto=validate`, `V1__baseline.sql` from today's schema, repeatable `R__` for views. Zero downtime with 2 replicas = **expand/contract**: add nullable column/table → deploy dual-write → backfill in batches → switch reads → drop in a later release; `CREATE INDEX CONCURRENTLY` outside a transaction (Flyway per-script `executeInTransaction=false`, verify), `SET lock_timeout` in scripts.
- **Money:** `BigDecimal` in code, `NUMERIC(19,2)` + `currency CHAR(3)` per order and per line (snapshot unit price, tax, discount at order time; totals computed server-side; documented `RoundingMode.HALF_EVEN`). Gateways want integer minor units (`amount_in_cents`); convert at the adapter. JavaMoney/Moneta (`Money.ofMinor`) only when multi-currency pricing arrives. COP's ISO 4217 exponent is 2 even though prices have no cents; verify and keep the scale.
- **Time:** `TIMESTAMPTZ` + `Instant`; JVM and Jackson in UTC; `America/Bogota` only for display/reporting boundaries; Modulith Moments (`DayHasPassed`) is a neat way to trigger daily analytics rollups.
- **IDs:** keep `BIGINT IDENTITY` internally; add a public **UUIDv7** (`@UuidGenerator(style = VERSION_7)` in Hibernate 7; PostgreSQL 18 has `uuidv7()`, on 16 generate in app) for URLs/idempotency; human order numbers from a sequence.
- **Soft delete vs archive:** products → `status`/`archived_at` (orders reference them); customers → anonymization; do not soft-delete orders. Hibernate `@SoftDelete` only where a restore path exists.
- **Auditing:** Spring Data auditing (`@CreatedDate/@LastModifiedBy` + `AuditorAware` from the SecurityContext) on all entities, keep the append-only audit log for security/admin events and the stock ledger for inventory; Envers only for entities needing full history (product price, order); three mechanisms is enough.
- **State machines:** plain enums + explicit transition table (`EnumMap<Status, Set<Status>>`) in the aggregate, each `transition()` publishes a domain event. **Spring Statemachine is commercial-only since April 2025 (4.0.x last OSS)**: do not adopt. Orders: CREATED → PENDING_PAYMENT → PAID → FULFILLING → SHIPPED → DELIVERED, with CANCELLED/EXPIRED/REFUNDED; Payments mirror the gateway's final states (APPROVED, DECLINED, VOIDED, ERROR); Shipments PENDING → LABEL_CREATED → IN_TRANSIT → DELIVERED/RETURNED; Repairs RECEIVED → DIAGNOSED → QUOTED → APPROVED → IN_REPAIR → READY → DELIVERED.
- **Inventory:** `stock_item(product_id, warehouse_id, on_hand, reserved, version)` + ledger rows carrying `warehouse_id`; one default warehouse now, allocation strategy later.
- **Performance:** index every FK, composite `(status, created_at)`, partial indexes for active reservations; `pg_stat_statements` + `EXPLAIN (ANALYZE, BUFFERS)`; DTO projections/`@EntityGraph`, `spring.jpa.open-in-view=false`. HikariCP: start from `(cores*2)+spindles` of the **DB host** (SSD → +1), i.e. ~5–10 per replica, keep total under `max_connections`, `max-lifetime` below Postgres/Nginx idle timeouts. Read replicas: not before metrics prove read pressure; then route only `analytics` through a routing DataSource.
- **PostgreSQL:** 16.15 is the current 16.x; PG 17 adds incremental backups, `MERGE … RETURNING`, `JSON_TABLE`; PG 18 (Sept 2025) adds `uuidv7()`, B-tree skip scan, async I/O; PG 19 is in beta (Aug 2026). Plan a jump to 18 when convenient. Backups: **pgBackRest** (full/diff/incremental, WAL archiving for PITR, S3/GCS, encryption, checksums) with a monthly restore drill; nightly `pg_dump` as a second copy.
- **Testing:** Testcontainers 2.0.x with `@ServiceConnection` (Postgres + Redis), `@ApplicationModuleTest` per module, Modulith `verify()` as the ArchUnit gate, resilience4j 2.4.0 (`resilience4j-spring-boot4`) with **timeouts + retry with jitter (idempotent calls only) + circuit breaker + bulkhead** on gateway/carrier/email/AI adapters. Pact/Spring Cloud Contract only if the Angular client is built by someone else; for one dev, the generated OpenAPI client plus e2e tests is the pragmatic contract.
- **Caching:** Caffeine (catalog, config; seconds-to-minutes TTL) + Redis (sessions, rate limits, shared listing cache); invalidate on domain events (`ProductUpdated` → `@CacheEvict`), never cache per-user data in shared keys.

## D. Prioritized checklists

**1. Must have before selling real products**
- Boot 4.1.x + Java 25 + Flyway baseline + `ddl-auto=validate`
- Cookie sessions (Spring Session/Redis) + `csrf.spa()` + logout/revocation; JWT removed from `localStorage`
- NIST-style password policy + HIBP check + per-account throttling + generic errors
- TOTP MFA for ADMIN/OPERATOR; admin on separate host with IP allow-list
- Hosted redirect checkout, server-side integrity signature, signed + idempotent webhooks, order paid only after gateway confirmation; fail-closed error handling
- BOLA scoping on all customer resources; DTOs; Bucket4j limits on login/reset/checkout
- Security headers + HSTS + TLS at Nginx; Cloudflare proxy
- Privacy policy + consent capture + data export/anonymize endpoints
- pgBackRest backups with one tested restore; secrets out of Git
- Trivy + Dependabot/Renovate + SBOM in CI; Testcontainers integration tests; Modulith `verify()`

**2. Should have in the first months**
- Postgres-backed job queue (db-scheduler/JobRunr) for emails/webhooks/imports with retries; resilience4j on every adapter
- Passkeys for admins; step-up MFA; audit log review dashboard
- Problem Details everywhere; OpenAPI-generated Angular client; keyset pagination
- Idempotency-Key on all money-moving POSTs
- Expand/contract migration discipline; `pg_stat_statements` review; index pass
- ZAP baseline + CodeQL/Semgrep in CI; incident runbook; log PII masking and retention
- SOPS+age; Cloudflare custom WAF rules; RNBD registration check (verify threshold)

**3. Later**
- Event externalization (outbox mode) to RabbitMQ; Debezium only with Kafka
- Multi-currency with JavaMoney; multi-warehouse allocation; read replica for analytics
- Keycloak/Spring Security authorization server when SSO, mobile or partner APIs appear
- PostgreSQL 18/19 upgrade; Envers on selected aggregates; Pact if a second frontend team exists
- Vault or cloud secret manager; Cloudflare Pro; formal ASVS L2 assessment

## E. Glossary

- **Modular monolith**: one deployable with enforced internal module boundaries.
- **Spring Modulith**: Spring project for modules, boundary verification, events, docs.
- **Bounded context**: DDD term: a model with its own language and boundary.
- **Named interface**: Modulith package explicitly exposed as part of a module API.
- **Hexagonal / ports and adapters**: domain depends on interfaces; providers plug in as adapters.
- **Domain event**: immutable fact ("OrderPaid") published after a state change.
- **Event Publication Registry**: Modulith's persisted log of event deliveries (outbox-like).
- **Transactional outbox**: write event and data in one transaction; relay later.
- **Debezium / CDC**: reads Postgres WAL to stream changes (needs Kafka Connect).
- **Namastack / JobRunr outbox**: Modulith 2.1 outbox-mode externalizers.
- **RabbitMQ / Kafka**: message broker / distributed log; neither needed now.
- **db-scheduler**: single-table Postgres job scheduler, Apache-2.0.
- **JobRunr**: LGPL job library with dashboard and retries.
- **ShedLock**: distributed lock so `@Scheduled` runs on one replica.
- **Quartz**: older scheduler with an 11-table schema.
- **Idempotency-Key**: header so retried POSTs execute once (IETF draft).
- **RFC 9457 Problem Details**: standard JSON error body (`application/problem+json`).
- **springdoc-openapi**: generates OpenAPI 3 + Swagger UI from Spring code.
- **Keyset pagination**: paginate by "after id/timestamp" instead of offset.
- **CQRS-lite**: separate read models for reporting from the write model.
- **Materialized view**: precomputed query result stored as a table.
- **Virtual threads (JEP 444) / JEP 491**: lightweight JVM threads / pinning fix in JDK 24+.
- **Jakarta EE 11 / Servlet 6.1**: API baseline of Spring Boot 4.
- **Jackson 3 (`tools.jackson`)**: JSON library generation used by Boot 4.
- **`InetAddressFilter`**: Boot 4.1 outbound-address block list against SSRF.
- **Flyway**: versioned SQL migrations; `ddl-auto=validate` checks mapping only.
- **Expand/contract**: backward-compatible schema change in phases for zero downtime.
- **BigDecimal / JavaMoney (JSR 354, Moneta)**: exact decimal math / money-with-currency API.
- **Minor units**: integer smallest currency unit (cents), as gateways expect.
- **TIMESTAMPTZ / Instant**: UTC-based timestamp types in Postgres / Java.
- **UUIDv7 (RFC 9562)**: time-ordered UUID, index-friendly, non-enumerable.
- **Soft delete / archive / anonymization**: flag rows / move rows / strip PII but keep records.
- **Spring Data auditing**: auto-fills created/modified by/at.
- **Hibernate Envers**: full entity history in `_AUD` tables.
- **Stock ledger**: append-only record of every inventory movement.
- **Optimistic locking**: `@Version` column detects concurrent updates.
- **State machine (enum transition table)**: allowed status transitions encoded explicitly.
- **Spring Statemachine**: Spring project, commercial-only since 2025.
- **HikariCP**: JDBC pool; size by DB cores, not app threads.
- **`pg_stat_statements`**: Postgres extension tracking query stats.
- **Read replica**: streaming copy of the DB for read-only queries.
- **pgBackRest / PITR**: backup tool / restore to any point via WAL archive.
- **Testcontainers / `@ServiceConnection`**: Docker-backed test dependencies auto-wired by Boot.
- **ArchUnit**: library asserting architecture rules in tests.
- **Pact / Spring Cloud Contract**: consumer-driven contract testing tools.
- **resilience4j**: retry, circuit breaker, bulkhead, time limiter, rate limiter.
- **Caffeine**: in-process cache; **Redis**: shared cache/store.
- **BFF (backend for frontend)**: server that holds tokens and gives the browser only a cookie (RFC 10017).
- **Spring Session**: `HttpSession` stored in Redis/JDBC across replicas.
- **CSRF / `csrf.spa()`**: forged cross-site requests / Spring Security 7 SPA config.
- **XSRF-TOKEN / X-XSRF-TOKEN**: cookie/header pair Angular sends automatically.
- **SameSite / HttpOnly / Secure / `__Host-`**: cookie attributes limiting cross-site use, JS access, HTTP, and host scope.
- **Refresh token rotation**: new refresh token per use; reuse triggers revocation.
- **Spring Authorization Server**: OAuth2/OIDC server, now part of Spring Security 7.
- **Keycloak**: open-source identity provider (OIDC, MFA, SSO).
- **Auth0 / Clerk / Supabase Auth**: hosted identity services.
- **NIST SP 800-63B-4**: US digital identity guideline (passwords, MFA, AAL levels).
- **HIBP Pwned Passwords / k-anonymity**: breached-password check by 5-char hash prefix.
- **BCrypt / Argon2id**: slow password hashes.
- **TOTP / passkeys (WebAuthn, FIDO2)**: time-based codes / phishing-resistant public-key login.
- **Step-up MFA**: extra factor required for sensitive routes.
- **OWASP Top 10 / API Top 10 / ASVS 5.0**: risk lists / verification standard with L1–L3.
- **BOLA / BOPLA / IDOR**: object, property-level authorization failures / direct object reference abuse.
- **SSRF**: server tricked into making internal requests.
- **CSP / HSTS / COOP / CORP**: security response headers.
- **SOPS + age / Vault / cloud secret manager**: encrypted files in Git / secrets server / managed secrets.
- **Dependabot / Renovate**: dependency update bots.
- **Trivy / Grype / OWASP Dependency-Check**: vulnerability scanners (the last needs an NVD key).
- **SBOM / CycloneDX**: bill of materials of dependencies / its format.
- **CodeQL / Semgrep / SonarQube**: static analysis (SAST) / code quality.
- **OWASP ZAP**: dynamic scanner (DAST).
- **Bucket4j**: token-bucket rate limiter (Redis/JDBC backends).
- **Cloudflare WAF / Bot Fight Mode**: edge firewall / bot challenge.
- **PCI DSS 4.0.1 / SAQ A**: card-data standard / questionnaire for fully outsourced payments.
- **Hosted (redirect) checkout / integrity signature**: gateway page for card entry / server-side hash of order params.
- **Webhook checksum**: HMAC/hash proving the event came from the gateway.
- **Ley 1581/2012, Decreto 1377/2013, SIC, RNBD**: Colombian data-protection law, its regulation, the authority, and the database registry.
- **Incident response runbook**: written steps for a breach.

## F. Sources

- https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/ · https://spring.io/blog/2026/06/10/spring-boot-4/ · https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide · https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes · https://docs.spring.io/spring-boot/system-requirements.html · https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html · https://endoflife.date/api/spring-boot.json · https://spring.io/blog/2025/11/13/spring-framework-7-0-general-availability/
- https://spring.io/blog/2026/06/11/spring-modulith-2-1-ga-2-0-7-and-1-4-12-released/ · https://spring.io/blog/2025/11/21/spring-modulith-2-0-ga-1-4-5-and-1-3-11-released/ · https://docs.spring.io/spring-modulith/reference/fundamentals.html · https://docs.spring.io/spring-modulith/reference/events.html · https://docs.spring.io/spring-modulith/reference/verification.html · https://docs.spring.io/spring-modulith/reference/moments.html
- https://openjdk.org/jeps/491 · https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html
- https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/ · https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html · https://github.com/spring-projects/spring-boot/issues/48392 · https://springdoc.org/
- https://github.com/lukas-krecan/ShedLock · https://github.com/kagkarlsson/db-scheduler · https://github.com/jobrunr/jobrunr · https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html · https://microservices.io/patterns/data/polling-publisher.html
- https://github.com/spring-projects/spring-statemachine/issues/1161 · https://github.com/spring-attic/spring-statemachine
- https://docs.hibernate.org/orm/7.0/javadocs/org/hibernate/annotations/UuidGenerator.Style.html · https://www.postgresql.org/ · https://www.postgresql.org/docs/release/17.0/ · https://www.postgresql.org/docs/release/18.0/ · https://pgbackrest.org/ · https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
- https://github.com/JavaMoney/jsr354-ri/blob/master/moneta-core/src/main/asciidoc/userguide.adoc · https://www.baeldung.com/database-auditing-jpa
- https://spring.io/blog/2023/06/23/improved-testcontainers-support-in-spring-boot-3-1/ · https://raw.githubusercontent.com/spring-projects/spring-boot/v4.1.1/platform/spring-boot-dependencies/build.gradle · https://docs.pact.io/ · https://repo1.maven.org/maven2/io/github/resilience4j/resilience4j-spring-boot4/maven-metadata.xml · https://github.com/bucket4j/bucket4j
- https://www.rfc-editor.org/info/rfc10017/ · https://oauth.net/2/browser-based-apps/ · https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html · https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html · https://angular.dev/best-practices/security · https://docs.spring.io/spring-session/reference/index.html · https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html · https://docs.spring.io/spring-security/reference/whats-new.html · https://spring.io/blog/2025/09/11/spring-authorization-server-moving-to-spring-security-7-0/ · https://www.keycloak.org/2026/07/keycloak-2670-released
- https://csrc.nist.gov/pubs/sp/800/63/b/4/final · https://pages.nist.gov/800-63-4/sp800-63b.html · https://haveibeenpwned.com/api/v3 · https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html · https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html · https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html
- https://top10.owasp.org/2025 · https://top10.owasp.org/2025/0x00_2025-Introduction/ · https://github.com/OWASP/ASVS · https://github.com/OWASP/ASVS/tree/master/5.0/en · https://owasp.org/API-Security/editions/2023/en/0xa4-unrestricted-resource-consumption/ · https://owasp.github.io/www-project-secure-headers/best-practices/
- https://spring.io/blog/2024/05/24/sbom-support-in-spring-boot-3-3/ · https://appsecsanta.com/owasp-dependency-check · https://developers.cloudflare.com/waf/rate-limiting-rules/ · https://developers.cloudflare.com/use-cases/solutions/stop-malicious-bots/ · https://dev.to/instadevops/secrets-management-vault-aws-secrets-manager-or-sops-2ce1
- https://blog.pcisecuritystandards.org/faq-clarifies-new-saq-a-eligibility-criteria-for-e-commerce-merchants · https://www.schellman.com/blog/pci-compliance/important-pci-dss-v4.0.1-update-for-e-commerce-merchants · https://docs.wompi.co/docs/colombia/widget-checkout-web/ · https://docs.wompi.co/docs/colombia/eventos/
- http://www.secretariasenado.gov.co/senado/basedoc/ley_1581_2012.html · https://www.suin-juriscol.gov.co/viewDocument.asp?ruta=Leyes/1684507

Verify list: Idempotency-Key draft status in 2026, Spring Framework 7 native API versioning, Flyway per-script `executeInTransaction`, duplicate handling of republished events across replicas, COP minor units, RNBD asset threshold and SIC breach-notification timelines, CodeQL licensing for private repos, other Colombian gateways' hosted-checkout details.
