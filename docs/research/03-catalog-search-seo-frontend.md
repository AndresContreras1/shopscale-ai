# Research: catalog model, search, media, SEO and modern Angular storefront practices

> Research notes, September 2026. Items marked **verify** rest on official pages that did not state them explicitly.

## A. Recommended decisions

**Version reality check.** Angular 20 entered LTS on 2025-11-19 and LTS ends 2026-11-28; v21 (2025-11-19) is LTS, v22 (2026-06-03) is Active. Plan the v20 → v21 → v22 upgrade now; several items below are experimental on v20 and stable on v21/v22.

**1. Catalog model (Product → Variant → Offer, typed attributes).**
- Follow the commercetools model: a *Product* is an abstract parent; *Variants* are the sellable SKUs carrying price, images and option values; a *Product Type* defines typed attributes (enum, localized string, number, boolean, reference). Option attributes that create variants: `color`, `storage`, `edition`, `region`. Descriptive attributes that do not: `platform`, `generation`, `model_number` (the console revision code), `connectivity`.
- **Condition and grading:** put `condition` (new | refurbished | used) plus `cosmetic_grade` (A | B | C) on the variant, because Google Merchant requires `condition` for used/refurbished and schema.org offers exactly `NewCondition | RefurbishedCondition | UsedCondition | DamagedCondition`. Back Market's scale (Premium/Excellent/Good/Fair) makes the point explicit: grade describes cosmetics only; every unit is functionally tested. Copy that rule into the grading page and into every PDP.
- **Compatibility:** a first-class `ConsoleModel` entity (brand, family, generation, model number) with a many-to-many `compatible_with` from spare-part variants; expose a "find your model" filter like Framework's marketplace parameters and iFixit's device pages.
- **Bundles:** a `bundle` product type referencing variant SKUs with quantities (commercetools' static-bundle guidance); set Merchant `is_bundle=yes`.
- **Digital goods:** variant flag `fulfillment=digital`, a `license_keys` table (encrypted, one-time reveal, region attribute), no shipping, non-returnable flag (verify local consumer law wording).
- **Services:** product type `service` with `diagnostic_fee`, `requires_device_intake`, `lead_time_days`; ordering creates a repair ticket; the final repair is a second line item or an adjustment.
- **Pricing:** money rows per currency (`amount_minor`, `currency`): `list`, `sale` with `valid_from/valid_through`, `compare_at` (maps to `StrikethroughPrice`), `cost` (internal only). Merchant Center: prices include VAT outside US/CA, ISO 4217 code, decimal point.
- **Identifiers:** `sku` (yours, no whitespace), `gtin` (EAN-13/UPC-12), `mpn`, `brand`; `identifier_exists=no` for generic parts. Warranty: `warranty_months`, `warranty_type` (manufacturer | store), `coverage_notes`.
- **Taxonomy example:** Consoles (PlayStation, Xbox, Nintendo, Retro; sub by generation) · Controllers (by platform; third-party) · Accessories (audio, charging, storage, cables, protection) · Games (physical by platform; digital keys; gift cards) · Spare parts (by console model: sticks/Hall-effect modules, HDMI ports, drives, shells, batteries, fans, thermal) · Repair services (diagnostic, HDMI, sticks, cleaning) · Refurbished (mirrors Consoles/Controllers with grade filter). Map each to a `google_product_category`.

**2. Search: PostgreSQL MVP → Meilisearch upgrade.**
- MVP: a `tsvector` generated column using a custom config that chains `unaccent` before `spanish_stem` (`ALTER TEXT SEARCH CONFIGURATION … WITH unaccent, spanish_stem`), `websearch_to_tsquery`, `ts_rank` plus boosts (in-stock, margin), and `pg_trgm` GIN (`gin_trgm_ops`, supports `ILIKE`/similarity) for typos and SKU/model-number partials. Facets = grouped counts on indexed attribute columns; Redis caches facet responses.
- Upgrade when facets or typo quality hurt: **Meilisearch** (typo tolerance and ranking rules out of the box, SQL-like filters, synonyms, Spanish normalization strips diacritics, single binary; HA only in Enterprise; Cloud from ~$20–30/mo). Alternative **Typesense** if you need OSS HA (Raft) or Snowball stemming via `locale` + `stem: true` (Spanish stemming support: verify). Skip OpenSearch/Elasticsearch (JVM tuning, shards, ~$26/mo managed minimum) and Algolia (cloud-only; free 10k searches/50k records, then $0.50/1k requests + $0.40/1k records).
- Indexing: transactional outbox table in PostgreSQL written in the same transaction as the product change; a Spring worker drains it and upserts flattened "search documents" (one per product with variant facets, or one per variant for grade/price filters); nightly full reindex.

**3. Media pipeline.** Originals in **Cloudflare R2** ($0.015/GB-month, zero egress) → on-the-fly renditions via **imgproxy** (OSS, libvips, WebP/AVIF/JPEG XL, signed URLs, S3-compatible source) behind Cloudflare cache; or Cloudflare Images transformations on the R2 origin (5,000 unique transformations free, then $0.50/1k). Cloudinary/imgix are per-transformation SaaS with lock-in; Supabase transformations need Pro and run imgproxy underneath. Frontend: `NgOptimizedImage` with a custom loader pointing at imgproxy (auto `srcset`/`sizes`, lazy by default, `priority` + `fetchpriority=high` for the LCP image, `placeholder` blur, mandatory width/height). Uploads: admin gets a presigned POST with `content-length-range` and content-type conditions; after the object lands, the API validates magic bytes (Tika), stores dimensions, and queues renditions. Moderation (Rekognition) only for customer-uploaded review/repair photos. Alt text is a required admin field defaulted to "product name + variant + view".

**4. SSR strategy (Angular 20).** Route-level render modes and incremental hydration are stable in v20. `Prerender` home, category landings, policies; `Server` for PDP and filtered listings (or `Prerender` the top N via `getPrerenderParams` with `Server` fallback); `Client` for cart, checkout, account. `provideClientHydration(withEventReplay(), withIncrementalHydration())` (v20 explicit; v22 default), `@defer (hydrate on viewport)` for reviews, related products, footer. `RESPONSE_INIT` returns 404/410 for missing products; `REQUEST` reads `Accept-Language`.

**5. UI stack.** Tailwind CSS v4 (official Angular guide: `@tailwindcss/postcss`, `.postcssrc.json`, `@use "tailwindcss"` in `styles.scss`) + **spartan/ui** (1.0 stable, 55+ components; brain layer = ARIA/keyboard, helm layer = styled code copied into your repo, signals/zoneless/SSR-ready). You own every pixel, which is exactly what "not AI-looking" requires. Angular Material M3 stays the fallback for heavy widgets (datepicker) only.

**6. i18n.** `@jsverse/transloco` (runtime switching, lazy scopes per feature, SSR support, signals API) with locale-prefixed routes (`/es-co/`, later `/en/`). Locale data via `@angular/common/locales` + `LOCALE_ID='es-CO'`, `DEFAULT_CURRENCY_CODE='COP'`, `currency:'COP':'symbol':'1.0-0'` (COP display without decimals: verify with `Intl`). Keep prices per currency in the API; never convert client-side.

**7. Testing.** Vitest via `@angular/build:unit-test` (experimental on v20, default on v21; migration schematic `refactor-jasmine-vitest`), Playwright e2e via `playwright-ng-schematics` (Angular ≥18, `ng e2e`), Storybook (Angular 18–22; signal inputs only partially supported in controls), `ng add @angular-eslint/schematics` (flat config) + Prettier.

## B. Comparison tables

**Search engines (small–medium catalog)**

| | PostgreSQL FTS+pg_trgm | Meilisearch | Typesense | OpenSearch/ES | Algolia |
|---|---|---|---|---|---|
| Typo tolerance | trigram similarity, manual | default | per-query fuzziness (verify default) | fuzzy queries | default |
| Facets | manual GROUP BY | yes | yes | yes | yes |
| Synonyms | dictionary work | yes | yes (10k/index) | yes | yes |
| Spanish | snowball + unaccent | normalization, no stemming documented | Snowball via locale | analyzers | yes |
| Ranking | ts_rank + SQL | orderable rules | custom rules | full scoring | rules |
| Ops | none extra | single binary | single binary, Raft HA | heaviest | none (cloud) |
| Cost | $0 | free self-host; Cloud ~$20–30/mo | free self-host; cloud hourly | ~$26/mo managed | free tier then usage |

**UI libraries**

| | Angular Material 3 | PrimeNG 20 | spartan/ui | Tailwind v4 custom | daisyUI |
|---|---|---|---|---|---|
| Look control | tokens (`--mat-sys-*`, `light-dark()`, overrides) but reads as Google | styled/unstyled + Tailwind plugin | full (code copied in) | full | themed presets, generic |
| A11y | strong | good | brain layer | yours | yours |
| Zoneless/SSR | yes | yes | yes | n/a | n/a |
| Risk | branded look | stability complaints (anecdotal) | young 1.0 | effort | generic look |

**i18n libraries**

| | @angular/localize | Transloco | ngx-translate |
|---|---|---|---|
| Time | build-time, one bundle per locale | runtime | runtime |
| Switching | reload/subpath only | in-app | in-app |
| Files | XLIFF/XMB | JSON, lazy scopes | JSON |
| SSR | native | plugin | community |
| Fit | fixed locales, max perf | growing locales, CMS content | legacy |

## C. SEO and performance checklist (what + how in Angular 20)

1. **Render modes**: `app.routes.server.ts` with `RenderMode.Prerender/Server/Client`; `getPrerenderParams` for top products.
2. **Hydration**: `withEventReplay()`; avoid direct DOM APIs and invalid HTML (tables need `<tbody>`); `ngSkipHydration` only as a patch.
3. **Titles/meta/OG**: `Title` + `Meta` services in a route resolver; canonical `<link>` via `DOCUMENT` (self-referencing per paginated page).
4. **Structured data**: JSON-LD injected server-side per route: `Product` + `Offer` (`price`, `priceCurrency`, `availability`, `itemCondition`, `shippingDetails`, `hasMerchantReturnPolicy`, `priceValidUntil`, `sku`, `gtin`, `brand`), `ProductGroup` (`hasVariant`, `variesBy`, `productGroupID`) with one canonical base URL per group, `AggregateRating`, `BreadcrumbList`, `Organization`. `FAQPage` remains valid markup but no longer renders rich results (dropped 2026).
5. **Pagination**: `?page=n` URLs, `<a href>` links between pages, no fragments, `noindex` on sort/filter permutations; rel next/prev unused by Google.
6. **Sitemaps**: `/sitemap.xml` served by an Express route before the Angular handler (verify pattern), 50k URLs/50MB per file, sitemap index, honest `lastmod`, `Sitemap:` line in static `robots.txt`.
7. **404/410**: `RESPONSE_INIT` status; Google treats both alike and drops the URL; avoid soft 404s (render a real 404 page with the status).
8. **Slugs**: `/consolas/playstation-5-slim-1tb-digital` with numeric fallback id; 301 (strong signal) on slug changes.
9. **Merchant Center**: Colombia supported with COP and Spanish; generate a feed (TSV/XML or Content API) with `id, title, description, link, image_link (≥500×500 from 2027), availability, price, brand, gtin/mpn, condition, item_group_id, product_type, google_product_category, sale_price`.
10. **hreflang (later)**: `es-CO`, `es`, `en`, `x-default`; one method only (sitemap scales best), absolute URLs, bidirectional.
11. **Core Web Vitals (p75)**: LCP ≤2.5s (SSR HTML + `priority` image + CDN + no lazy on hero), INP ≤200ms (zoneless + OnPush, no long tasks on add-to-cart), CLS ≤0.1 (image dimensions, skeletons with real sizes, `@placeholder` blocks). Measure with `web-vitals` in production and CrUX/PSI.
12. **Zoneless**: `provideZonelessChangeDetection()` (stable from 20.2), remove `zone.js` from polyfills, `await fixture.whenStable()` in tests.
13. **Data**: `httpResource` for reads after v21 (experimental in v20), `HttpClient` for mutations; interceptors for auth, retry, error mapping.
14. **Bundles**: `budgets` in `angular.json`, lazy routes, `@defer` for reviews/carousels, `prefetch on idle`.
15. **Analytics**: `gtag('consent','default',{all denied})` before the tag, `update` after the CMP; GA4 ecommerce events (`view_item`, `add_to_cart`, `begin_checkout`, `purchase`…) from a service on `NavigationEnd` and cart actions.
16. **PWA**: `ng add @angular/pwa` for asset caching only (Angular SW is maintenance-mode); never cache cart/checkout APIs.
17. **A11y (WCAG 2.2 AA)**: visible focus not hidden by sticky header (2.4.11), 24×24 targets (2.5.8), no drag-only (2.5.7), no redundant entry in checkout (3.3.7), consistent help placement (3.2.6); CDK `LiveAnnouncer` for cart updates, `cdkTrapFocus` in drawers, focus to main on route change, `ariaCurrentWhenActive`, 4.5:1 contrast, labelled inputs, ESLint a11y rules.

## D. Design principles to avoid the generic AI look

- Real photography on one consistent neutral background, plus in-scale shots (Baymard: 37% of sites lack scale cues) and honest condition photos for refurbished units.
- One distinctive display typeface + one workhorse body face; product names as technical identifiers (Teenage Engineering, Analogue) not slogans.
- Restrained palette: neutral surfaces, one brand accent, a semantic set for stock/condition/grade; no purple gradients, glass cards, or three-icon feature rows.
- Fixed layout rhythm: 8px scale, 12-column grid, one card ratio per context, dense spec tables.
- Status-driven merchandising ("Shipping now", "Pre-order", "Grade B") as the visual hierarchy (Analogue, Back Market).
- Compatibility as UI: platform badges per controller (8BitDo), "select your console model" gate for parts (Framework, iFixit).
- Grade selectors as buttons with explanations, never dropdowns (Baymard: 57% fail); total cost, return policy and warranty next to the CTA.
- Micro-interactions with intent: add-to-cart drawer + announced confirmation, `animate.enter/leave` (20.2+, verify) and `withViewTransitions()` (developer preview) for PLP→PDP image continuity; respect `prefers-reduced-motion`.
- Designed empty/loading/error states: skeletons sized like final content, error copy that says what to do next.
- Copy in specific Spanish with real specs; no "Bienvenido a nuestra tienda".
- Study: analogue.co, teenage.engineering, 8bitdo.com, frame.work, ifixit.com, backmarket.com (grading UX), plus local expectations at alkosto.com, ktronix.com, mercadolibre.com.co (verify current designs).

## E. Glossary

- **Product / Variant / SKU**: abstract parent; sellable unit; merchant identifier.
- **Product Type / attribute**: schema of typed fields per product family.
- **GTIN / EAN / UPC / MPN**: global trade number; its 13/12-digit forms; manufacturer part number.
- **Cosmetic grade A/B/C**: wear level; function guaranteed regardless.
- **Bundle**: product referencing other SKUs with quantities.
- **Digital good / license key**: non-shipped item delivered as a code.
- **Outbox pattern**: event row written in the same DB transaction, drained by a worker.
- **tsvector / websearch_to_tsquery / ts_rank**: PostgreSQL FTS index type, query parser, ranking.
- **unaccent / spanish_stem**: diacritics-stripping filter dictionary; Snowball Spanish stemmer.
- **pg_trgm / GIN**: trigram similarity extension; inverted index type.
- **Meilisearch / Typesense / OpenSearch / Algolia**: OSS search engines (single binary; Raft HA), ES fork, hosted search SaaS.
- **Facets / synonyms / typo tolerance / ranking rules**: filter counts; term equivalences; fuzzy matching; relevance ordering.
- **R2 / S3 / Supabase Storage**: object storage services.
- **imgproxy / Cloudinary / imgix / Cloudflare Images**: image transformation server (OSS) and SaaS equivalents.
- **libvips**: fast image processing library behind imgproxy.
- **WebP / AVIF / JPEG XL**: modern compressed image formats.
- **srcset / sizes / picture**: responsive image selection attributes and element.
- **NgOptimizedImage**: Angular directive enforcing image best practices.
- **Presigned URL / POST policy**: time-limited upload authorization with conditions.
- **Rekognition / Tika**: AWS moderation API; MIME sniffing library.
- **SSR / SSG / CSR**: server, build-time, client rendering.
- **RenderMode / ServerRoute / getPrerenderParams**: per-route render config APIs.
- **Hydration / event replay / incremental hydration**: attach app to server HTML; replay early clicks; hydrate `@defer` blocks lazily.
- **REQUEST / RESPONSE_INIT**: SSR DI tokens for request data and response status.
- **JSON-LD / Product / Offer / ProductGroup / BreadcrumbList / Organization / AggregateRating / FAQPage**: structured-data format and schema.org types.
- **Canonical / hreflang / x-default**: preferred URL; language alternates; fallback alternate.
- **Sitemap index / robots.txt**: list of sitemaps; crawler rules file.
- **Soft 404 / 410**: error page returning 200; "gone" status.
- **Merchant Center / product feed / google_product_category**: Google Shopping backend; product export; Google taxonomy.
- **LCP / INP / CLS / p75 / CrUX**: Core Web Vitals; percentile rule; Chrome field dataset.
- **fetchpriority / preload**: resource priority hints.
- **Signals / computed / linkedSignal / effect**: Angular reactive primitives.
- **Zoneless / OnPush**: change detection without zone.js; opt-in checking strategy.
- **resource() / httpResource / HttpClient / interceptor**: signal-based async loaders; HTTP service; request middleware.
- **@defer / control flow (@if, @for)**: lazy template blocks; built-in template syntax.
- **Standalone / typed forms / Signal Forms**: NgModule-free components; typed reactive forms; signal-based forms (v22).
- **Guard / resolver**: route access check; route data preloader.
- **NgRx SignalStore**: signal-based feature store.
- **Transloco / ngx-translate / @angular/localize**: runtime i18n libs; build-time i18n.
- **LOCALE_ID / DEFAULT_CURRENCY_CODE / registerLocaleData / Intl**: locale tokens and data; browser formatting API.
- **WCAG 2.2 AA / ARIA / CDK a11y / LiveAnnouncer / cdkTrapFocus**: accessibility standard; semantics attributes; Angular a11y toolkit.
- **PWA / service worker / manifest**: installable web app; caching script; app metadata.
- **GA4 / gtag / GTM / Consent Mode v2**: analytics platform; tag script; tag manager; consent signalling.
- **Vitest / Playwright / Storybook / angular-eslint / Prettier**: unit runner; e2e; component workshop; linter; formatter.
- **Bundle budgets / Angular DevTools**: size limits in `angular.json`; profiler extension.
- **Tailwind v4 / spartan/ui (brain, helm) / Angular Material M3 / PrimeNG / daisyUI**: utility CSS; headless + copied styled components; Google design system lib; component suite; Tailwind theme plugin.
- **Design tokens / `--mat-sys-*` / `light-dark()`**: named design values; Material system variables; CSS color-scheme function.
- **animate.enter/leave / View Transitions API / withViewTransitions**: Angular CSS-class animation hooks; native page transitions; router integration.
- **Baymard**: e-commerce UX research institute.

## F. Sources

- https://angular.dev/guide/ssr · https://angular.dev/guide/incremental-hydration · https://angular.dev/guide/hydration · https://angular.dev/guide/zoneless · https://v20.angular.dev/guide/http/http-resource · https://angular.dev/guide/http/http-resource · https://angular.dev/guide/templates/defer · https://angular.dev/guide/image-optimization · https://angular.dev/guide/animations · https://angular.dev/api/router/withViewTransitions · https://angular.dev/guide/tailwind · https://angular.dev/guide/testing/migrating-to-vitest · https://v20.angular.dev/guide/testing/unit-tests · https://angular.dev/ecosystem/service-workers · https://angular.dev/best-practices/a11y · https://angular.dev/guide/i18n/merge · https://angular.dev/guide/i18n/deploy · https://angular.dev/guide/i18n/import-global-variants · https://angular.dev/roadmap · https://angular.dev/reference/releases
- https://raw.githubusercontent.com/angular/components/main/guides/theming.md · https://github.com/jsverse/transloco · https://dev.to/playfulprogramming-angular/announcing-spartanui-10-546o · https://github.com/playwright-community/playwright-ng-schematics · https://storybook.js.org/docs/get-started/frameworks/angular · https://github.com/angular-eslint/angular-eslint/releases · https://nx.dev/blog/angular-state-management-2025
- https://developers.google.com/search/docs/appearance/structured-data/merchant-listing · https://developers.google.com/search/docs/appearance/structured-data/product-variants · https://developers.google.com/search/docs/specialty/ecommerce/pagination-and-incremental-page-loading · https://developers.google.com/search/docs/specialty/international/localized-versions · https://developers.google.com/search/docs/crawling-indexing/http-network-errors · https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap · https://support.google.com/merchants/answer/7052112 · https://support.google.com/merchants/answer/160637 · https://developers.google.com/tag-platform/security/guides/consent · https://developers.google.com/analytics/devguides/collection/ga4/ecommerce · https://www.searchenginejournal.com/google-drops-faq-rich-results-from-search/574429/ · https://schema.org/OfferItemCondition
- https://web.dev/articles/vitals · https://web.dev/articles/optimize-lcp · https://developer.mozilla.org/en-US/docs/Web/HTML/Guides/Responsive_images · https://www.levelaccess.com/blog/wcag-2-2-aa-summary-and-checklist-for-website-owners/
- https://www.postgresql.org/docs/current/pgtrgm.html · https://www.postgresql.org/docs/current/unaccent.html · https://www.postgresql.org/docs/current/textsearch-dictionaries.html · https://www.meilisearch.com/docs/learn/resources/comparison_to_alternatives · https://www.meilisearch.com/docs/resources/help/language · https://www.meilisearch.com/pricing · https://typesense.org/typesense-vs-algolia-vs-elasticsearch-vs-meilisearch/ · https://typesense.org/docs/28.0/api/stemming.html · https://typesense.org/docs/28.0/api/collections.html · https://www.algolia.com/pricing · https://www.meilisearch.com/blog/opensearch-alternatives · https://github.com/raedbh/spring-outbox
- https://docs.imgproxy.net/ · https://developers.cloudflare.com/images/pricing/ · https://supabase.com/docs/guides/storage/serving/image-transformations · https://www.cloudflare.com/pg-cloudflare-r2-vs-aws-s3/ · https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html · https://cloudinary.com/documentation/aws_rekognition_ai_moderation_addon
- https://docs.commercetools.com/learning-model-your-product-catalog/product-modeling/products · https://docs.commercetools.com/foundry/best-practice-guides/product-bundles · https://help.backmarket.com/hc/en-us/articles/360026656634-What-condition-will-my-device-be-in · https://baymard.com/blog/current-state-ecommerce-product-page-ux · https://www.analogue.co/ · https://teenage.engineering/ · https://www.8bitdo.com/ · https://frame.work/ · https://www.ifixit.com/

Verify list: Typesense Spanish stemming coverage, spartan/ui 1.0 release date, `animate.enter` introduction version, Express sitemap route pattern, COP decimal formatting via `Intl`, current design of the Colombian retailers listed.
