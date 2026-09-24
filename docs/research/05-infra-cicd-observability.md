# Research: production infrastructure, CI/CD, observability, reliability and costs

> Research notes, September 2026. Prices are approximate, taken from the pages listed at the end; "verify" marks anything that could not be confirmed on an official page.

## A. Recommended path

**Latency reality check (WonderNetwork, avg ping from Bogotá):** Miami 60 ms · New York 73 ms · Washington DC ≈ us-east-1 77 ms · Santiago 68 ms · São Paulo 114 ms · Frankfurt 167 ms. **US East beats São Paulo (sa-east-1 / southamerica-east1 / brazilsouth) by ~35–40 ms for Colombian users.** In-country compute exists only at Oracle Cloud (Bogotá region `sa-bogota-1`); AWS Local Zone Bogotá (`us-east-1-bog-1a`) is "request interest", not GA. Cloudflare has PoPs in Bogotá, Medellín, Cali and Barranquilla, so static assets and prerendered HTML are served in-country regardless of origin. No SA region at Railway, Render, DigitalOcean, Hetzner or Contabo; Fly.io has only `gru` (São Paulo); Vultr has São Paulo, Santiago, Mexico City and Miami.

### MVP (a few hundred orders/month): ~$45–60/month

| Piece | Choice | ≈ USD/mo |
|---|---|---|
| Compute | One VPS in **Vultr Miami** or **DigitalOcean nyc3/atl1**, 2 vCPU / 4 GB (DO Premium AMD $26, Basic $24; Vultr 2 vCPU/4 GB ≈ $20 verify). Avoid Hetzner US (CPX11 2 GB = $20.49 since 15-Jun-2026); Hetzner EU is cheap (CX23 €5.49) but ~160 ms | 26 |
| Snapshots/backups | provider weekly backups (DO ≈ +20%, verify) | 5 |
| Runtime | Docker Compose + **Dokploy** or **Coolify** (self-hosted, free) or plain Compose + `docker-rollout`; **Caddy** (auto-TLS) or keep Nginx with a Cloudflare Origin CA cert; API ×2 replicas, Angular SSR Node container, Redis container | 0 |
| PostgreSQL | **DO Managed Postgres 1 GB $15** (daily backups + PITR, no ops), or self-host PG16 + **pgBackRest → Cloudflare R2** with a monthly restore drill ($0) | 15 |
| Redis | container in Compose (or Upstash Free: 256 MB / 500K cmds) | 0 |
| Edge | **Cloudflare Free**: proxy, DDoS, Free Managed WAF ruleset, 70 custom rules, Bot Fight Mode, Universal SSL, cache rules | 0 |
| Object storage | **R2** (10 GB + 1M Class A + 10M Class B free; $0 egress) for product images | 0–1 |
| Email | **Resend Free** (3,000/mo, 100/day) → SES $0.10/1,000 when volume grows; SPF+DKIM+DMARC (`p=none` → `quarantine`) | 0 |
| Domain | `.co` ≈ $15.76 first year / $31.20 renewal (Porkbun); also register `.com`; `.com.co` via .CO-accredited registrars (price verify) | 3 |
| Observability | Grafana Cloud Free (10k series, 50 GB logs, 50 GB traces, 14 d), Sentry Developer (5k errors, 1 user) or self-hosted GlitchTip, Better Stack Free (10 monitors, 30-s checks, 1 status page, on-call) | 0 |
| GitHub | Free works for a public repo; for a **private** repo take **Pro ($4)**: environments/approvals and 10 GB/mo package transfer (Free = 1 GB, and VPS pulls of a private GHCR image count) | 0–4 |

Why not PaaS for MVP: Railway (US East, Pro $20 + $20/vCPU + $10/GB → 2× 1 vCPU/1 GB API ≈ $60 + DB) lands ≈ $100/mo; Fly.io `iad` (2× shared-cpu-1x 2 GB $11.11 + Managed Postgres from $38) ≈ $70/mo; Render (Pro workspace $25 + Standard 1c-2g ≈ $25 each, verify) ≈ $100/mo. They buy zero-downtime deploys and no server patching, which is worth it if you would rather not run a VPS at all.

### Growth (thousands of orders/month): ~$150–300/month, two valid paths

- **G1 – VPS scale-out (~$150–250):** second app VPS behind Cloudflare, DO Managed Postgres **HA** (2 GB primary + standby, $30+$30), Managed Valkey $15, **Cloudflare Pro $20/mo** (full managed rulesets, Polish, Super Bot Fight Mode with skip rules), Grafana Cloud Pro $19 base, Sentry Team $26. Deploy multi-host with **Kamal** (kamal-proxy zero-downtime) or Dokploy multi-server.
- **G2 – Managed containers, AWS us-east-1 (~$150–250):** ECS **Fargate** on ARM (2 tasks × 0.5 vCPU/1 GB ≈ $29; $0.03236/vCPU-h, $0.00356/GB-h) + ALB (≈$16 + LCU, verify) + **RDS PostgreSQL db.t4g.small** ($0.032/h ≈ $23 Single-AZ, ≈ $47 Multi-AZ, + gp3 $0.115/GB) + ElastiCache Serverless Valkey (≈$6 minimum + ECPUs) + ECR/CloudWatch ≈ $10. **App Runner stopped accepting new customers on 30-Apr-2026; AWS points to ECS Express Mode**: use ECS. Cloud Run / Azure Container Apps are equivalent (Cloud Run instance-based ≈ $53/mo per always-on 1 vCPU/1 GiB in Tier-1 US regions; São Paulo is a higher tier, verify; ACA has a 180k vCPU-s/360k GiB-s/2M-request monthly free grant). São Paulo regions cost ~30–40% more than Virginia and are slower for Colombia: stay in us-east-1 unless data residency forces otherwise.

**Triggers MVP → Growth:** sustained CPU > 60% on the VPS or need for >2 API replicas at peaks; DB RAM/connection pressure; manual ops (patching, restores) > ~4 h/month; revenue such that one hour of downtime > one month of the HA premium; a payment-provider or marketplace requiring an uptime SLA.
**Triggers Growth → Kubernetes:** > ~5 services, many deploys/day across several environments, or a second engineer. Managed control planes cost ≈ $73/mo (EKS/GKE $0.10/h; GKE credits $74.40/mo for one zonal/Autopilot cluster; AKS Free tier $0 without SLA). **k3s** on VPS is free but adds upgrade/etcd/networking on-call for one person; ECS/Cloud Run give 90% of the benefit.
**Peak (Black-Friday-like):** on G2, target-tracking autoscaling (CPU/ALB request count) with min tasks raised the day before; on G1, pre-scale the VPS (per-second billing at DO since 1-Jan-2026); scale the DB instance class ahead, add a read replica for catalog reads, cache catalog + sessions in Redis; Cloudflare caches prerendered pages; run k6 at 3–5× forecast peak two weeks before; freeze deploys during the event.

**Data residency (Ley 1581/2012, Decreto 1377/2013):** transfers abroad are allowed to countries the SIC deems adequate, with the data subject's express consent, or when necessary to perform a contract; SIC Circular 003/2025 recommends the Ibero-American Network model clauses otherwise. RNBD registration applies to companies with assets > 100,000 UVT (a small store is usually exempt, verify). Practical: privacy policy + checkout consent stating data is processed in the US/EU; keep card data with the PSP so you never store PAN.

## B. Hosting comparison

| Option | ≈ Cost MVP → growth | Ops burden | Scaling | Bogotá latency | Notes |
|---|---|---|---|---|---|
| VPS + Compose (DO nyc/atl, Vultr Miami) + Coolify/Dokploy | $25–30 → $80–150 | Medium (you patch, back up, rotate) | Vertical, then manual 2nd host | 60–75 ms | Best $/perf; Vultr Miami closest |
| Hetzner | EU: CX23 €5.49; US: CPX11 $20.49 | Medium | Vertical | EU ~160 ms; US ~77 ms | US pricing no longer cheap; 20 TB traffic in EU |
| Contabo | 4 vCPU/8 GB €5.50 (24-mo, intro, VAT incl.) | Medium–high (no backups included) | Vertical | US ~75–90 ms | No LatAm; weak sustained CPU (third-party) |
| Fly.io | 2 GB machine $11.11; MPG from $38 | Low–medium | Horizontal, per-second | `iad` ~77 ms; `gru` 114 ms | RAM price rising 1-Oct-2026 (verify) |
| Railway | Pro $20 + $20/vCPU + $10/GB | Low | Horizontal | US East ~77 ms | Simple; costs grow linearly |
| Render | Pro $25 + instance (1c-2g ≈ $25 verify) | Low | Horizontal | Virginia/Ohio ~77 ms | Flat tiers since Apr-2026 (third-party) |
| AWS ECS Fargate (us-east-1) | ≈ $60–80 → $150–250 incl. RDS | Low–medium (IaC needed) | Autoscaling in minutes | ~77 ms | App Runner closed; ECS Express Mode successor |
| Cloud Run / Azure Container Apps | ≈ $50–110 always-on ×2 | Low | Scale-to-zero, request autoscaling | US East ~77 ms; Santiago/Chile Central ~68 ms | Free tiers; SP tier pricier |
| Managed K8s (EKS/GKE/AKS) | +$73/mo control plane + nodes | High | Best | same regions | Only with >5 services or 2+ engineers |
| Oracle Cloud Bogotá (Always Free) | $0 (2 A1 OCPU/12 GB, 200 GB, 10 TB egress) | Medium–high | Vertical | <10 ms | Only in-country region; idle reclamation (<20% use over 7 d); verify free-tier capacity in Bogotá |

## C. CI/CD pipeline and deployment strategy

**Branching/versioning:** trunk-based with short PRs; **Conventional Commits**; **release-please** (`maven` release type keeps `pom.xml` versions and opens a release PR; `fix:`→patch, `feat:`→minor, `!`→major); tag `vX.Y.Z` = image tag; deploy by immutable digest.

**GitHub Actions stages (all on `pull_request` except deploy):**
1. **Build & unit tests**: Maven with dependency cache; `actions/setup-java` Temurin.
2. **Integration tests**: Testcontainers (PostgreSQL 16, Redis) on `ubuntu-latest` (Docker preinstalled); Flyway migrations run inside the test.
3. **Lint/format**: Spotless/Checkstyle; ESLint + `ng build --configuration production` for Angular.
4. **SAST**: CodeQL default setup, Java **build mode `none`** (no compile step). Free on public repos; **private repos need GitHub Code Security (Team/Enterprise plan, per-committer, verify price)**; otherwise Semgrep Community CLI or SpotBugs + find-sec-bugs (free).
5. **Dependencies**: Dependabot alerts/updates (free on all repos) + `trivy-action` `scan-type: fs` (`severity: CRITICAL,HIGH`, `exit-code: 1`, `ignore-unfixed: true`).
6. **Docker build**: multi-stage: JDK build → `java -Djarmode=tools -jar app.jar extract --layers --destination extracted` → CDS training run `java -XX:ArchiveClassesAtExit=application.jsa -Dspring.context.exit=onRefresh -jar app.jar` → runtime `eclipse-temurin:<ver>-jre-jammy` (or distroless), `USER 1000`, `ENTRYPOINT ["java","-XX:SharedArchiveFile=application.jsa","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]`. Push to **GHCR** by digest.
7. **Image scan + SBOM + signature**: `trivy-action` `scan-type: image` (SARIF upload needs Code Security on private repos → keep `format: table` and fail on CRITICAL); `anchore/sbom-action` (Syft, `spdx-json`, `dependency-snapshot: true`); **cosign keyless** (`permissions: id-token: write, packages: write`; `cosign sign` + `cosign attest --type spdxjson`).
8. **k6 smoke** against staging: `grafana/setup-k6-action` + `grafana/run-k6-action` (`path`, `flags`, `fail-fast`); thresholds in the script fail the job; nightly full load test.
9. **Deploy**: GitHub **Environments** `staging` (auto) and `production` (required reviewer = you, wait timer, deployment branch = `main`/tags). Private-repo environments require Pro/Team. Cloud creds via **OIDC** (`aws-actions/configure-aws-credentials` with `id-token: write`, 1-h role credentials, no stored keys); for a VPS use an SSH deploy key scoped to a deploy user, or let Dokploy/Coolify pull on webhook.

**Environments:** `dev` = Docker Compose locally with Spring Boot's Compose support; `staging` = same Compose file on the prod host (separate DB/Redis, `*.staging.` subdomain) or a $6–12 VPS; `prod`. Parity via the same image digest and `SPRING_PROFILES_ACTIVE`.

**IaC:** **OpenTofu** (MPL, drop-in Terraform fork with built-in state encryption; Terraform is BSL 1.1) for Cloudflare zone/DNS/cache/WAF rules, R2, DO/Vultr droplets, and all AWS resources in G2; Docker Compose stays the workload definition on VPS. Pulumi only if you prefer TypeScript/Java over HCL.

**Deployment strategy:** VPS = rolling via `docker-rollout` (scales the service ×2, waits for healthcheck, removes old; service must not use `container_name`/`ports`; proxy routes by network) or Kamal; ECS = rolling with `minimumHealthyPercent=100`, circuit breaker + auto-rollback; blue-green/canary only when you have traffic to measure (ALB weighted target groups). **Rollback** = redeploy previous digest (one command; keep last 5 tags).

**DB migrations (Flyway, zero-downtime):** expand → deploy → backfill → switch → contract; old app must run on the new schema and vice versa; never drop/rename a column in the same release; run `flyway migrate` as a **separate one-off step before the rollout** (`spring.flyway.enabled=false` in replicas) to avoid lock contention/startup coupling across replicas. **Feature flags:** OpenFeature Java SDK (`dev.openfeature:sdk`, `setProviderAndWait`) with a DB/env provider now; swap to **Unleash OSS** (free, 1 project/2 environments) or **Flagsmith** (BSD-3 core) later without changing call sites. **GitOps/Argo CD** only if you go to Kubernetes.

## D. Observability & reliability checklist

**Must-have (MVP)**
- [ ] Structured JSON logs: `logging.structured.format.console=ecs` (Boot ≥ 3.4); Micrometer Tracing (`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`) adds `traceId/spanId` to MDC (`logging.pattern.correlation`); `management.tracing.sampling.probability` (default 0.1); Angular SSR logs JSON with the same request id.
- [ ] Metrics/traces/logs → **Grafana Cloud Free** via Grafana Alloy (Prometheus scrape of `/actuator/prometheus`, Loki for logs, Tempo for traces). OTel Java agent is the zero-code default; use the Micrometer/starter path when the agent's startup overhead or a conflicting agent matters.
- [ ] RED dashboard (rate, errors, duration per endpoint) + USE for host (CPU, memory, disk); business panel: orders/hour, checkout conversion, payment failures, repair tickets opened.
- [ ] Error tracking: Sentry (Spring Boot + Angular SDKs; Developer free) or GlitchTip self-hosted.
- [ ] Uptime + status page + on-call: Better Stack Free (30-s checks, phone/SMS) or UptimeRobot Free (50 monitors, 5-min, personal-use clause, verify) / Upptime (GitHub-hosted, 5-min).
- [ ] Synthetic checkout: k6 browser or Grafana Synthetics (free: 100k API / 10k browser executions) hitting a test SKU with the PSP sandbox every 15 min.
- [ ] Alerts (Grafana Alerting or Alertmanager): 5xx rate > 2%/5 min, p95 latency > 1.5 s, DB connections > 80%, disk > 80%, cert expiry < 14 d, backup job failed, no orders in 3 h during business hours.
- [ ] Backups: managed PITR, or pgBackRest full weekly + incremental daily + WAL archiving to R2 (`archive_command='pgbackrest --stanza=main archive-push %p'`); **restore drill monthly**, record RTO. Targets: **RPO ≤ 15 min, RTO ≤ 2 h** (MVP); RPO ≤ 5 min / RTO ≤ 30 min (growth, HA DB).
- [ ] TLS: Cloudflare Full (strict) + Origin CA cert (free, 15-year) on Caddy/Nginx; origin firewall allows only Cloudflare IPs + your SSH.
- [ ] Container hardening: non-root, read-only root FS + `tmpfs /tmp`, `cap_drop: [ALL]`, `HEALTHCHECK` on `/actuator/health/readiness`, `mem_limit`/`cpus` per service, JVM `MaxRAMPercentage=75`, CDS archive (AOT cache replaces it on Java 25+).
- [ ] Cloudflare cache rules: `/api/*` bypass; hashed assets `Cache-Control: immutable, max-age=1y`; prerendered pages edge-TTL 60–300 s, bypass when `Cookie` contains the session; Bot Fight Mode **cannot be skipped per path**: test PSP webhooks; if blocked, move to Pro's Super Bot Fight Mode with a skip rule or disable BFM.
- [ ] Angular SSR: hybrid `app.routes.server.ts`: `RenderMode.Prerender` for home/categories/policies, `Server` for product/repair-service pages (SEO + live stock), `Client` for cart/checkout/account; Node server via `@angular/ssr/node` in its own container.
- [ ] Runbooks (deploy, rollback, restore DB, rotate secrets, PSP outage, "site down" triage) in the repo `docs/`; blameless postmortem template within 48 h.
- [ ] Cost alerts: AWS Budgets, provider billing alerts, Cloudflare R2 usage notifications; monthly cost review.
- [ ] Secrets: OIDC in CI; on VPS `.env` (mode 600) or Docker secrets; rotate DB/Redis/PSP/API tokens quarterly and on any offboarding; scoped Cloudflare tokens.

**Later (growth)**
- [ ] HA Postgres (managed standby / Multi-AZ) + read replica; Redis with persistence/HA (Upstash Prod Pack $200 or ElastiCache).
- [ ] Grafana Cloud Pro ($19 base; $6.50/1k series) or self-hosted LGTM (Loki/Grafana/Tempo/Mimir) on a $12 VPS when free limits bind; 30–90 d retention for SLO reporting.
- [ ] SLOs: availability 99.5 % (MVP) → 99.9 %; checkout success ≥ 99 %; error-budget alerting (burn rate).
- [ ] Cloudflare Pro WAF managed rulesets + rate limiting; Business ($200) only if you need the 100 % SLA.
- [ ] Canary deploys with ALB weighted target groups; DR: cross-region snapshot copy + IaC to rebuild in a second region within RTO; quarterly game day.

## E. Glossary

- **ACME**: protocol Caddy/Traefik/certbot use to get Let's Encrypt certs automatically.
- **AKS/EKS/GKE**: managed Kubernetes of Azure/AWS/Google; control plane ≈ $73/mo except AKS Free.
- **ALB**: AWS Application Load Balancer; routes HTTP to ECS tasks.
- **AOT cache (JEP 483)**: Java 25+ successor to CDS for faster startup.
- **App Runner**: AWS container PaaS; closed to new customers 30-Apr-2026.
- **Argo CD**: GitOps controller that syncs Kubernetes state from Git.
- **Azure Container Apps (ACA)**: serverless containers with a monthly free grant.
- **Backblaze B2**: cheap S3-compatible storage ($6.95/TB), free egress via Cloudflare.
- **Blue-green / canary / rolling**: deploy patterns: full switch / small % first / replace instances gradually.
- **Bot Fight Mode / Super BFM**: Cloudflare bot challenges (Free / Pro+ with skip rules).
- **Caddy / Traefik / Nginx**: reverse proxies: auto-HTTPS simplicity / Docker auto-discovery / maximum control with certbot.
- **CDS / AppCDS**: JVM class-data-sharing archive that cuts Spring Boot startup 30–50 %.
- **Cloud Run**: Google serverless containers; request- or instance-based billing.
- **CodeQL**: GitHub SAST; free on public repos, paid Code Security on private.
- **Conventional Commits / release-please / semantic-release**: commit convention and tools that derive versions and changelogs from it.
- **Coolify / Dokploy**: self-hosted PaaS UIs over Docker on your VPS.
- **cosign / Sigstore / Fulcio / Rekor**: keyless image signing with GitHub OIDC identity and a transparency log.
- **Crunchy Bridge**: managed Postgres; Hobby tier not for production.
- **Dependabot**: GitHub dependency alerts/updates (free).
- **Distroless / Temurin JRE**: minimal runtime base images for Java.
- **docker-rollout**: script for zero-downtime Compose updates.
- **DMARC/DKIM/SPF**: DNS records that authenticate your sending domain.
- **ECS Fargate / ECS Express Mode**: AWS serverless containers; Express Mode = simplified ECS setup recommended after App Runner.
- **ElastiCache Serverless**: AWS managed Redis/Valkey billed per GB-hour + ECPU.
- **Expand/contract**: backward-compatible schema migration pattern.
- **Feature flags / OpenFeature / Unleash / Flagsmith**: runtime toggles; vendor-neutral SDK; two open-source servers.
- **Flyway**: SQL migration tool bundled with Spring Boot.
- **GHCR**: GitHub Container Registry (public free; private metered).
- **GitHub Environments / OIDC**: deployment gates with approvals; short-lived cloud credentials without stored keys.
- **GlitchTip / Sentry**: error tracking (open-source Sentry-compatible / SaaS).
- **Grafana Cloud / Alloy / Loki / Tempo / Mimir / Prometheus**: hosted or self-hosted metrics, logs, traces stack and collector.
- **Hybrid rendering / SSR / prerender**: Angular per-route rendering modes.
- **IaC / Terraform / OpenTofu / Pulumi**: infrastructure as code; BSL-licensed original / MPL fork / general-purpose-language alternative.
- **k3s**: lightweight Kubernetes for a single VPS.
- **k6**: Grafana load-testing tool; runs in CI via GitHub Actions.
- **Kamal**: 37signals' Docker deploy tool with zero-downtime proxy.
- **LCU**: ALB capacity unit (usage part of ALB cost).
- **Ley 1581/2012 / SIC / RNBD**: Colombian data-protection law, its regulator, and the database registry.
- **Local Zone**: AWS edge compute tied to a parent region (Bogotá announced).
- **MaxRAMPercentage**: JVM flag sizing heap as % of container memory.
- **MDC / correlation id**: per-request context (traceId/spanId) injected into logs.
- **Micrometer / OTel Java agent / OTLP**: Spring metrics/tracing facade; zero-code instrumentation agent; OpenTelemetry wire protocol.
- **Neon / Supabase**: serverless Postgres (scale-to-zero) / Postgres platform with Pro at $25.
- **pgBackRest / PITR / WAL**: Postgres backup tool; restore to a point in time via write-ahead logs.
- **PoP**: CDN point of presence.
- **PSP**: payment service provider (tokenizes cards).
- **R2**: Cloudflare S3-compatible storage with zero egress.
- **RED / USE**: Rate-Errors-Duration (services) / Utilization-Saturation-Errors (resources).
- **RPO / RTO**: max data loss / max downtime targets.
- **Runbook / postmortem**: step-by-step ops procedure / blameless incident write-up.
- **SARIF / SBOM / Syft / Trivy**: scan-result format / bill of materials / SBOM generator / vulnerability scanner.
- **SES / Resend / Postmark**: transactional email providers (cheapest / dev-friendly / deliverability-focused).
- **SLO / error budget / burn rate**: reliability target and alerting on its consumption.
- **Testcontainers**: throwaway Docker DBs for integration tests.
- **Upstash / Redis Cloud / Valkey**: serverless Redis / Redis Inc. hosting / open-source Redis fork.
- **Upptime / UptimeRobot / Better Stack**: uptime monitors and status pages.
- **UVT**: Colombian tax value unit used for RNBD thresholds.

## F. Sources

Hosting: [Railway plans](https://docs.railway.com/pricing/plans) · [Railway regions](https://docs.railway.com/reference/regions) · [Render compute plans](https://render.com/docs/compute-plans) · [Render regions](https://render.com/docs/regions) · [Render cost article](https://render.com/articles/how-much-does-cloud-application-hosting-cost-for-small-businesses) · [Fly.io pricing](https://fly.io/docs/about/pricing/) · [Fly.io Oct-2026 update](https://fly.io/pricing-update/) · [Fly.io regions](https://fly.io/docs/reference/regions/) · [Fargate pricing](https://aws.amazon.com/fargate/pricing/) · [App Runner notice](https://aws.amazon.com/apprunner/) · [Cloud Run pricing](https://cloud.google.com/run/pricing) · [ACA pricing](https://azure.microsoft.com/en-us/pricing/details/container-apps/) · [Hetzner price adjustment](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/) · [DO droplets](https://www.digitalocean.com/pricing/droplets) · [DO regions](https://docs.digitalocean.com/platform/regional-availability/) · [Vultr (getdeploying)](https://getdeploying.com/vultr) · [Contabo VPS](https://contabo.com/en/vps/) · [Coolify pricing](https://coolify.io/pricing) · [Dokploy pricing](https://dokploy.com/pricing) · [K8s pricing comparison](https://sedai.io/blog/kubernetes-cost-eks-vs-aks-vs-gke) · [OCI Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
Regions/latency: [WonderNetwork Bogotá pings](https://wondernetwork.com/pings/Bogota) · [AWS Local Zones](https://aws.amazon.com/about-aws/global-infrastructure/localzones/locations/) · [Providers in Colombia](https://getdeploying.com/datacenters-in-colombia) · [Cloudflare Bogotá](https://blog.cloudflare.com/bogota) · [GCP locations](https://cloud.google.com/about/locations) · [Azure regions](https://learn.microsoft.com/en-us/azure/reliability/regions-list) · [Colombia data transfer (DLA Piper)](https://www.dlapiperdataprotection.com/index.html?t=transfer&c=CO)
Data: [RDS t4g.micro](https://instances.vantage.sh/aws/rds/db.t4g.micro) · [RDS t4g.small](https://instances.vantage.sh/aws/rds/db.t4g.small) · [Neon pricing](https://neon.com/pricing) · [Neon regions](https://neon.com/docs/introduction/regions) · [Supabase pricing](https://supabase.com/pricing) · [Supabase regions](https://supabase.com/docs/guides/platform/regions) · [Crunchy Bridge](https://docs.crunchybridge.com/concepts/plans-pricing) · [DO Postgres](https://docs.digitalocean.com/products/databases/postgresql/details/pricing/) · [DO Valkey](https://docs.digitalocean.com/products/databases/valkey/details/pricing/) · [Upstash](https://upstash.com/docs/redis/overall/pricing) · [Redis Cloud](https://redis.io/pricing/) · [ElastiCache](https://aws.amazon.com/elasticache/pricing/) · [R2](https://developers.cloudflare.com/r2/pricing/) · [B2](https://www.backblaze.com/cloud-storage/pricing) · [Cloudflare plans](https://www.cloudflare.com/plans/network-cdn.md) · [Bot Fight Mode](https://developers.cloudflare.com/bots/get-started/bot-fight-mode/) · [Cache Rules](https://developers.cloudflare.com/cache/how-to/cache-rules/settings/) · [SES](https://aws.amazon.com/ses/pricing/) · [Resend](https://resend.com/pricing) · [Postmark](https://postmarkapp.com/pricing) · [.co at Porkbun](https://porkbun.com/tld/co)
Delivery: [GitHub Actions billing](https://docs.github.com/en/billing/managing-billing-for-your-products/managing-billing-for-github-actions/about-billing-for-github-actions) · [Packages billing](https://docs.github.com/en/billing/managing-billing-for-your-products/managing-billing-for-github-packages/about-billing-for-github-packages) · [Environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments) · [OIDC → AWS](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services) · [Advanced Security](https://docs.github.com/en/get-started/learning-about-github/about-github-advanced-security) · [CodeQL compiled languages](https://docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/codeql-code-scanning-for-compiled-languages) · [trivy-action](https://github.com/aquasecurity/trivy-action) · [sbom-action](https://github.com/anchore/sbom-action) · [cosign keyless guide](https://www.qcecuring.com/blog/sigstore-cosign-keyless-github-actions) · [Spring Boot Dockerfiles](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html) · [Spring Boot CDS](https://docs.spring.io/spring-boot/3.5/reference/packaging/class-data-sharing.html) · [Expand-contract](https://ankurm.com/zero-downtime-database-migrations-expand-contract-spring-boot/) · [OpenFeature Java](https://github.com/open-feature/java-sdk) · [Unleash pricing](https://www.getunleash.io/pricing) · [release-please](https://github.com/googleapis/release-please) · [Kamal](https://kamal-deploy.org/) · [docker-rollout](https://github.com/wowu/docker-rollout) · [IaC comparison](https://www.env0.com/guides/pulumi-vs-terraform-vs-opentofu-side-by-side-feature-licensing-and-migration-comparison-2026) · [Proxy comparison](https://dokploy.com/blog/caddy-vs-traefik-vs-nginx)
Observability/reliability: [Spring Boot logging](https://docs.spring.io/spring-boot/reference/features/logging.html) · [Spring Boot tracing](https://docs.spring.io/spring-boot/reference/actuator/tracing.html) · [OTel Spring Boot starter](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/) · [Grafana pricing](https://grafana.com/pricing/) · [Sentry pricing](https://sentry.io/pricing/) · [GlitchTip pricing](https://glitchtip.com/pricing/) · [Better Stack pricing](https://betterstack.com/uptime/pricing) · [UptimeRobot pricing](https://uptimerobot.com/pricing/) · [Upptime](https://upptime.js.org/) · [run-k6-action](https://github.com/grafana/run-k6-action) · [pgBackRest guide](https://pgbackrest.org/user-guide.html) · [Angular SSR](https://angular.dev/guide/ssr) · [AWS Budgets pricing](https://aws.amazon.com/aws-cost-management/aws-budgets/pricing/)

Caveats: a few numbers rest on third-party 2026 pages (Render instance prices, Hetzner backup %, UptimeRobot non-commercial clause, Fly RAM increase, Cloud Run free-tier regions, Fargate/RDS São Paulo premium) and are marked "verify".
