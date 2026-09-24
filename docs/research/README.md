# Research reports

Inputs for [`docs/master-plan.md`](../master-plan.md), written in September 2026 for the transition from the ShopScale AI demo to a real console store with a repair workshop (Colombia first, international-ready).

Each report follows the same shape: recommended decisions, comparison tables, domain or checklist material, a **glossary** (every term named, one line each, meant as a study list) and the **sources** actually used.

| # | Report | Covers |
|---|---|---|
| 01 | [Payments, legal and fiscal (Colombia)](01-payments-legal-fiscal-colombia.md) | Gateways (Wompi, Mercado Pago, PayU, ePayco, dLocal, Addi), PCI SAQ A, webhooks and idempotency, IVA, DIAN electronic invoicing, Ley 1480 / 1581 / 527 / 2300 duties |
| 02 | [Shipping and fulfillment](02-shipping-fulfillment.md) | Carriers and aggregators, Colombian addresses (DIVIPOLA), order/shipment/return state machines, carrier adapter, COD, packaging, KPIs |
| 03 | [Catalog, search, SEO and frontend](03-catalog-search-seo-frontend.md) | Product/variant model, condition grading, compatibility, search engines, media pipeline, Angular SSR, structured data, Core Web Vitals, UI stack, i18n, testing, design principles |
| 04 | [Backend architecture and security](04-backend-architecture-security.md) | Modular monolith with Spring Modulith, events and outbox, jobs, idempotency, API conventions, Boot 4 / Java 25, Flyway, money, ids, state machines, sessions vs JWT, NIST passwords, MFA, OWASP, secrets, CI scanners |
| 05 | [Infrastructure, CI/CD and observability](05-infra-cicd-observability.md) | Hosting options and costs, latency to Colombia, managed data services, pipeline stages, deployment strategies, feature flags, observability stack, backups, runbooks |
| 06 | [AI for commerce and repairs](06-ai-commerce-repairs.md) | Spring AI 2, model landscape and pricing, embeddings and pgvector, use-case designs (search, recommendations, support, repair pre-diagnosis, listings, ops), governance |
| 07 | [Customers, notifications, marketing and support](07-customers-notifications-marketing-support.md) | Identity UX, email and WhatsApp providers, notification pipeline, promotions engine, reviews, consent model, support tools, analytics and consent |
| 08 | [Console repair and gaming retail domain](08-console-repair-retail-domain.md) | Repair ticket model and state machine, legal rules for repair services, retail additions (grading, trade-in, digital keys), compatibility matrix, playbooks, Colombian market references |

Caveats: prices, model ids and legal details change; items the researchers could not confirm on an official page are marked **verify** and must be checked before they are relied on (especially fiscal and legal items, with an accountant or lawyer).
