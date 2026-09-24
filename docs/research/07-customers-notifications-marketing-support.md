# Research: customer accounts, notifications, marketing/growth tooling and support

> Research notes, September 2026 (verified against official pages on 2026-09-23 where possible; items marked **verify** could not be confirmed).

## A. Recommended decisions

### MVP (launch, Colombia only)

| Area | Decision | Rationale |
|---|---|---|
| Identity | Keep email+password; add **email verification**, **OWASP-style password reset**, **guest checkout** (email + WhatsApp phone), **Google sign-in** via Spring `oauth2Login` (Google is built into `CommonOAuth2Provider`). Skip Apple/Facebook/passkeys for now. | Baymard: 18% of abandonments are caused by forced account creation; Google covers most Android-heavy Colombian users. Apple needs a rotating ES256 JWT client secret that Spring does not support natively (issue #9047 declined, #9471 dup). Passkeys: FIDO 2026 says 90% awareness / 75% enabled on ≥1 account, but the survey covers no LatAm country, so treat it as an add-on. |
| Transactional email | **Amazon SES** (à la carte US$0.10/1k; `sa-east-1` São Paulo region exists; new accounts land on the "Essentials" plan at US$0.16/1k from 2026-07-21) or **Postmark** (US$15/10k, best-in-class transactional focus, 45-day logs). Templates: **Thymeleaf** rendered in Spring, HTML produced from **MJML**. | Cheapest reliable options for a Java stack; Postmark if you want zero deliverability babysitting; SES if you want cost + region control. Resend stores all account data/logs in the US even with the `sa-east-1` sending region. |
| WhatsApp | **Meta Cloud API direct** (no BSP fee) for transactional templates (order confirmed, shipped, repair status), sent from the notification service; **Chatwoot** (self-hosted, MIT) as the shared inbox using Meta Embedded Signup. | Colombia has the cheapest utility rate in Meta's card (≈US$0.0008–0.0009/msg; marketing ≈US$0.0125–0.0138; **verify** on Meta's rate card). Free-form replies inside the 24-hour window are free. A BSP adds €49+/month (360dialog) or US$0.005/msg (Twilio) for nothing you need at MVP. |
| SMS | Not at launch; if needed, **Hablame** (COP $6/SMS incl. IVA, monthly minimum; REST API) for OTP fallback. | Twilio to Colombia costs ≈US$0.05–0.06 per segment, 10× a local provider. |
| Support | **Chatwoot** self-hosted (WhatsApp Cloud API, Instagram, email, live chat, help center) + order-status self-service page + WhatsApp "click-to-chat" with prefilled order number. | Free, MIT, Postgres+Redis (same infra), no per-agent fee for a 1–3 person team. Cloud Startups plan is US$19/agent if you prefer hosted. |
| Analytics | **GA4 via GTM** with the standard e-commerce events, **Meta Pixel + Conversions API** with `event_id` dedup, **Google Merchant Center** (Colombia supported, COP, Spanish, Shopping tab available), **Google Business Profile** for the repair shop. Consent banner with defaults + Consent Mode v2 signals (cheap now, mandatory later for EEA). | Everything Google/Meta needs for paid acquisition and free listings; Consent Mode v2 is only *required* for EEA/UK/CH ads, but implementing the four signals now avoids a re-tag later. |
| Legal baseline | Política de tratamiento + aviso de privacidad (Ley 1581 / Decreto 1377), explicit marketing checkbox (silence ≠ consent), retracto/reversión flows (Ley 1480 art. 47/50/51), RNBD registration if assets > 100,000 UVT (**verify**, Decreto 090/2018). | SIC enforces; consultas 10 business days, reclamos 15 business days. |

### Growth (months 3–12 and international)
- **Email marketing**: start with **Brevo** (EU company, Starter US$9, transactional included on every plan, generous 300/day free tier) or **Omnisend** (Standard US$11.20/500 contacts; Pro US$41.30 with unlimited email + SMS at US$0.007–0.009). Klaviyo (US$20/500 profiles, US$70/3,000) only if you need its flows/segmentation depth; its SMS table lists no Colombia (**verify**).
- **WhatsApp marketing at scale**: keep Cloud API direct; if you need campaign tooling, Colombian BSPs **Treble.ai** (official BSP since 2019, HubSpot/Salesforce native, demo pricing), **B2Chat** (Medellín; US$105/mo with WhatsApp, 2 users/2 channels), **Masiv** (Bogotá CPaaS: SMS/WhatsApp/RCS/email, Meta partner). 360dialog (€49/number/mo, no markup) if you want an EU BSP for exports.
- **Identity**: add **magic links** (`oneTimeTokenLogin()`, Spring Security 6.4+) for guest → account conversion, **passkeys** (`spring-security-webauthn`, 6.4+) as an opt-in, **Apple** only if you ship an iOS app.
- **Support**: stay on Chatwoot until >5 agents or you need multi-SLA/skills routing (then Freshdesk Omni US$29–79/agent or Zendesk Suite US$55–115/agent).
- **Experimentation**: **GrowthBook** (open-source, Java + JS SDKs; cloud free ≤3 users, Pro US$40/seat) or **PostHog** (1M events, 5K replays, 1M flag requests free/month).
- **Marketplaces**: Mercado Libre first (largest, full REST API), then Falabella Seller Center API, then Éxito.

## B. Comparison tables

### Email providers (list prices, Sep 2026)

| Provider | Entry paid tier | Overage | Free tier | Logs | Data location | Notes |
|---|---|---|---|---|---|---|
| Amazon SES | US$0.10/1k à la carte; Essentials plan US$0.16/1k (0–10M) for new accounts from 2026-07-21 | n/a | US$200 credits for new AWS accounts | via VDM (+US$0.07/1k) | 25+ regions incl. `sa-east-1`, `eu-west-1` | Sandbox 200/day, 1/s until production access; attachments US$0.12/GB; dedicated IP managed US$1,250/mo (Essentials) |
| Postmark | Basic US$15/10k; Pro US$16.50; Platform US$18 | US$1.80 / 1.30 / 1.20 per 1k | 100/mo | 45 days (up to 365 add-on) | US (**verify**, no EU option listed) | Transactional-only reputation; DMARC monitoring US$14/domain; dedicated IP US$50 |
| Resend | Pro US$20/50k, US$35/100k | US$0.90/1k | 3,000/mo, 100/day | 30 days | Sending regions us-east-1, eu-west-1, sa-east-1, ap-northeast-1; **all account data/logs in the US** | React Email native; dedicated IP US$30 (Scale, >3k/day) |
| SendGrid (Twilio) | Essentials US$19.95/50k; Pro US$89.95/100k | US$0.0013/email (Ess. 50k) | 60-day trial, 100/day | 3 days (Ess.), 7 days (Pro) | US; EU regional email on Pro (**verify**) | Pro includes 1 dedicated IP |
| Brevo | Starter US$9 (from 5k emails); Standard US$18; Professional US$499/150k | plan-based | 300/day | unlimited | EU (French company; **verify** DC) | Transactional API/SMTP/webhooks included on every plan, shared meter with marketing |
| Mailgun | Basic US$15/10k; Foundation US$35/50k; Scale US$90/100k | US$1.80 / 1.30 / 1.10 per 1k | 100/day | 1 / 5 / 30 days | US or EU region (pricing page says no extra cost; a third-party says +US$10, **verify**) | Scale includes 1 dedicated IP |

Deliverability baseline for all: SPF **and** DKIM, DMARC (≥`p=none`) with aligned From domain, valid PTR, TLS, spam rate <0.3% in Postmaster Tools, one-click unsubscribe (`List-Unsubscribe` + `List-Unsubscribe-Post`) on marketing mail (Google bulk-sender rules at 5,000+/day; Microsoft applies equivalent rules since 2025-05-05, **verify**). Send transactional from a subdomain (e.g. `tx.tienda.co`) and marketing from another; warm up gradually.

### WhatsApp providers

| Option | Platform fee | Message fees | Inbox | Colombia presence |
|---|---|---|---|---|
| Meta Cloud API direct | US$0 | Meta rates only | Build/BYO (Chatwoot) | n/a; COP billing available since 2026-04-01 |
| 360dialog | €49 / €99 / €500 per number/mo | Meta, no markup | Premium+ | EU BSP |
| Twilio | US$0.005/msg in+out (+US$0.001 failed) | Meta pass-through | No | Global |
| Wati | ≈US$59/119/279 per mo annual (US$69/149/349 monthly) (**verify**, official page shows MAC tiers 500/1,000/2,500) | Meta + ≈20% markup (third-party, **verify**) | Yes | Global |
| B2Chat | US$33 (no WA), US$105 (with WA, 2 users), US$187 (5 users) | Meta pass-through; extra unique contacts US$0.05 | Yes | Medellín, Meta Business Partner |
| Treble.ai | Demo/quote | Meta pass-through | Campaign + bots | Colombian official BSP |
| Masiv | Quote | Quote | CPaaS | Bogotá, Meta partner |

Meta rules: per-message billing since 2025-07-01; marketing always charged; utility templates and free-form messages free inside the 24-hour customer-service window; 72-hour free window after Click-to-WhatsApp ads; opt-in mandatory (state that they will receive WhatsApp messages from *your business name*; SMS/website/IVR/in-person accepted); templates auto-reviewed up to 24 h, statuses APPROVED/REJECTED/PAUSED/DISABLED, marketing templates paced; 250 templates unverified / 6,000 verified. Third-party blogs claim charges from 2026-10-01 for service messages beyond 1,000/number/month and for utility-in-window; **Meta's pricing doc fetched today does not show this: treat as unverified**. Authentication-in-window: Meta's page reads "free within" but Twilio's says billable, **verify**.

### Support tools

| Tool | Price | WhatsApp / Instagram | Help center | SLA / CSAT | Fit |
|---|---|---|---|---|---|
| Chatwoot | Self-hosted MIT free; cloud US$0 (2 agents, chat only, 500 conv/mo) / US$19 / US$39 / US$99 per agent | Yes from Startups (cloud) / included self-hosted | Yes | SLA policies on Enterprise (US$99) / CSAT yes | Best for 1–5 agents on your own Postgres/Redis |
| Crisp | Workspace pricing: Free (2 seats), Mini US$45 (4), Essentials US$95 (10), Plus US$295 (20+) | Pricing page lists WA/IG on all tiers (a third party says Essentials+, **verify**) | Essentials+ | Ticketing Essentials+, CSAT all | Flat price, good chat widget |
| Freshdesk | Support Desk US$19/55/89; Omni US$29/79/119 per agent (annual) | Omni | Yes | Multiple SLAs Pro+ | Classic ticketing; Freddy AI 500 sessions incl., US$49/100 extra |
| Zendesk | Support Team US$19; Suite Team US$55; Suite Pro US$115; Copilot +US$50 | Suite (social messaging; Meta fees passed through, **verify**) | Yes | Yes | Overkill for a single-vendor store |
| HubSpot Service Hub | Free (2 users); Starter ≈US$7–20/seat (promo vs list); Pro US$90–100/seat + US$1,500 onboarding | WhatsApp from Starter | Yes | SLAs, CSAT/NPS all tiers | Pick only if you also want HubSpot CRM |

## C. Domain design notes

**Promotions engine.** `Promotion{id, code?, type: PERCENT|FIXED|FREE_SHIPPING|BOGO|THRESHOLD_GIFT, value, scope: ORDER|ITEM|SHIPPING, conditions[], startsAt, endsAt, usageLimitTotal, usageLimitPerCustomer, firstOrderOnly, stackable: NONE|WITH_SHIPPING|ALL, priority, campaignId, status}`. `Condition` is a small rule set: min subtotal, category/brand/SKU in/out lists, customer segment, payment method, channel. Evaluate in `PromotionEvaluator(cart, customer) -> AppliedDiscount[]` in a fixed order (item-level → order-level → shipping), reject non-stackables after the first, persist `PromotionRedemption{promotionId, orderId, customerId, amount}` inside the order transaction (unique index on `promotionId+orderId`, count per customer for limits). Codes are case-insensitive, generated in bulk for campaigns (`CouponCode{code, promotionId, singleUse, redeemedBy}`). Flash sales = a scheduled `Promotion` plus a `priceListOverride` on the product read model; countdown is only UI. Bundles = a virtual SKU whose components decrement stock. Gift cards are a **payment method**, not a promotion (`GiftCard{code hash, balance, expiry}` with a ledger). Loyalty points are a separate ledger (`PointsTransaction{customerId, delta, reason, orderId, expiresAt}`) earned on delivered (not paid) status and reversed on refund. Referral = a coupon pair keyed to the referrer.

**Notification pipeline.** Domain events (`OrderPlaced`, `PaymentConfirmed`, `OrderShipped{tracking}`, `RepairStatusChanged`, `PasswordResetRequested`) are written to an `outbox` table in the same Postgres transaction (transactional outbox, at-least-once). A relay publishes to a `notification` worker (Redis stream or DB polling with `SELECT … FOR UPDATE SKIP LOCKED`). Worker resolves `NotificationPreference{customerId, channel, category, optedIn, updatedAt, source}` + channel rules (transactional always allowed; marketing requires opt-in), renders `Template{key, channel, locale, version, body}` (versioned, immutable; store the version used on each `Notification`), and calls a `ChannelAdapter` (`EmailAdapter(SES|Postmark)`, `WhatsAppAdapter(Cloud API template name + params)`, `SmsAdapter`). Idempotency key = `eventId + channel + templateKey`; unique index prevents double sends on retry. Retries with exponential backoff, DLQ after N attempts; provider webhooks (bounce/complaint/delivered/read/failed) update `Notification.status` and auto-suppress hard bounces and complaints. Consumer service window is tracked per phone (`lastInboundAt`) so WhatsApp uses free-form replies when allowed and templates otherwise.

**Reviews.** `Review{id, productId, customerId, orderId?, rating 1–5, title, body, photos[], verifiedPurchase (orderId delivered and contains productId), status: PENDING|APPROVED|REJECTED, moderationNote, helpfulVotes, createdAt}` with a unique `(productId, customerId)`. `ReviewRequest` is emitted N days after `OrderDelivered` (one per order, suppressible). Public aggregate is a materialized `ProductRating{avg, count, histogram}` refreshed on approval. Q&A is the same shape without rating. Expose `Product` + `AggregateRating` (`ratingValue`, `reviewCount`) structured data; never mark up self-serving reviews on `LocalBusiness` pages (Google forbids it); disclose incentivized reviews. Repair-shop reputation lives on Google Business Profile (reviews and responses feed "prominence").

**Consent.** `Consent{customerId|guestEmail, purpose: TERMS|PRIVACY_POLICY|MARKETING_EMAIL|MARKETING_WHATSAPP|MARKETING_SMS|COOKIES_ANALYTICS|COOKIES_ADS, granted, policyVersion, capturedAt, channel (web/checkout/WhatsApp), ip, userAgent, evidenceText}` append-only; current state is the latest row per purpose. Checkbox unchecked by default for marketing (Decreto 1377 art. 7: silence is never consent); privacy notice links to the full policy (art. 14–15). Marketing WhatsApp opt-in text must name the business. Preferences page + one-click unsubscribe update the ledger; support `revocar/suprimir` requests with a case that is answered within 15 business days and a data export (JSON of profile, addresses, orders, consents). Cookie banner stores the same purposes and feeds `gtag('consent','update',…)`.

## D. Prioritized checklist

**Must-have to launch**
- Email verification, password reset per OWASP (CSPRNG token, hashed at rest, single use, short expiry, uniform responses, invalidate sessions, rate limit), guest checkout, Google sign-in.
- Profile: address book (departamento/municipio/barrio, complemento, cédula/NIT for invoicing), phone with WhatsApp flag, marketing consents.
- Order history + tracking page (public URL with token for guests), repair-ticket status page.
- Transactional emails + WhatsApp utility templates: order confirmation, payment received, shipped (tracking), delivered, refund/retracto confirmation, repair received/diagnosed/ready, password reset, email verification.
- SPF/DKIM/DMARC, dedicated subdomain, webhooks for bounces/complaints, suppression list.
- Legal: privacy policy + notice, terms, retracto (5 business days, refund ≤15 calendar days per Ley 2439/2024, verify), reversión del pago (5 business days), PQR form, 1-year legal warranty display; order summary and acuse de recibo (art. 50).
- GA4 e-commerce events (`view_item`, `add_to_cart`, `begin_checkout`, `add_shipping_info`, `add_payment_info`, `purchase` with `transaction_id`), Meta Pixel + CAPI with `event_id`, consent defaults, Merchant Center feed, Google Business Profile.
- Chatwoot inbox (WhatsApp + Instagram + email + web widget), FAQ/help center, click-to-chat with order context.

**First months**
- Coupons v1 (percent/fixed/free shipping, per-customer limit, first order, expiry), abandoned-cart email 2–4 h then 24–48 h later (Klaviyo guidance), review requests after delivery, wishlist, back-in-stock alerts, notification preferences page, CSAT after ticket close, UTM discipline, Mercado Libre listing sync, blog/SEO for spare parts and repair guides, Discord/YouTube/TikTok presence.

**Later**
- Magic links, passkeys, Apple sign-in (with iOS app), loyalty points, referral, gift cards, pre-orders, bundles/flash sales, BOGO/threshold rules and stacking, WhatsApp marketing campaigns via a BSP, price-drop alerts, web push, A/B testing (GrowthBook/PostHog), Falabella/Éxito integrations, Consent Mode v2 with a certified CMP before selling into the EEA, data-export self-service, account deletion self-service.

## E. Glossary

- **Abandoned cart recovery**: timed reminders after an unpaid cart; 2–3 messages.
- **Acuse de recibo**: order receipt the seller must send (Ley 1480 art. 50).
- **AggregateRating**: schema.org rating summary for review snippets.
- **Amazon SES**: AWS email API, pay per 1k emails.
- **Apple Sign in**: OIDC provider needing a rotating JWT client secret.
- **Aviso de privacidad**: short privacy notice pointing to the full policy.
- **B2Chat**: Colombian WhatsApp inbox/BSP.
- **Back-in-stock alert**: notification when a SKU is restocked.
- **BOGO**: buy-one-get-one promotion.
- **Brevo**: French email/SMS marketing platform with transactional API.
- **BSP**: Meta WhatsApp Business Solution Provider.
- **Bundle**: virtual SKU composed of several products.
- **CAPI**: Meta Conversions API (server-side events).
- **Chatwoot**: open-source (MIT) omnichannel inbox.
- **Click-to-WhatsApp ad**: ad that opens a 72-hour free messaging window.
- **CMP**: consent management platform (cookie banner).
- **Consent Mode v2**: Google signals `ad_storage`, `analytics_storage`, `ad_user_data`, `ad_personalization`.
- **Crisp**: workspace-priced chat/support suite.
- **CSAT / NPS**: post-interaction satisfaction / loyalty surveys.
- **Customer service window**: 24 h after an inbound WhatsApp message; free-form replies allowed.
- **Dedicated IP**: sending IP used only by you; needs warm-up.
- **DLQ**: dead-letter queue for failed jobs.
- **DMARC / SPF / DKIM**: email authentication policies and signatures.
- **Embedded Signup**: Meta's onboarding flow for Cloud API.
- **event_id**: dedup key shared by Pixel and CAPI (48-hour window).
- **Falabella Seller Center API**: signed `Action` requests (XML) for products/orders.
- **FIDO / passkeys / WebAuthn**: passwordless public-key login.
- **Freshdesk / Zendesk / HubSpot Service Hub**: commercial help desks.
- **GA4 recommended events**: `view_item`, `add_to_cart`, `purchase`, etc.
- **Gift card**: prepaid balance used as a payment method.
- **Google Business Profile**: local listing; ranking = relevance, distance, prominence.
- **Google Merchant Center**: product feed for Shopping ads/free listings.
- **GrowthBook / PostHog**: open-source experimentation/analytics.
- **GTM**: Google Tag Manager.
- **Guest checkout**: buy without creating an account.
- **Hablame / Masiv / Inalambria**: Colombian SMS/CPaaS providers.
- **Idempotency key**: unique key preventing duplicate sends.
- **Klaviyo / Omnisend / Mailchimp**: e-commerce email marketing platforms.
- **Ley 1480/2011**: Colombian consumer statute (retracto, reversión, garantía).
- **Ley 1581/2012 + Decreto 1377/2013**: Colombian data protection law and regulation.
- **Ley 2439/2024**: cut e-commerce retracto refunds to 15 calendar days (verify).
- **List-Unsubscribe**: header enabling one-click unsubscribe (RFC 8058).
- **Magic link / OTT**: one-time token login sent by email/SMS.
- **Mailgun / Postmark / Resend / SendGrid**: transactional email APIs.
- **Mercado Libre API**: OAuth2 REST API (items, orders, shipments, questions).
- **Meta Pixel**: browser-side ad tracking script.
- **MJML / React Email / Maizzle**: email HTML frameworks (markup, React, Tailwind).
- **Outbox pattern**: events stored with the business transaction, relayed later.
- **Pre-order**: sale before stock arrives; charge at ship or upfront.
- **PQR**: peticiones, quejas y reclamos.
- **Promotion stacking**: rules for combining discounts.
- **Quality rating / pacing**: Meta template health controls.
- **Referral program**: reward for bringing a new customer.
- **Retracto**: 5-business-day withdrawal right for distance sales.
- **Reversión del pago**: card payment reversal within 5 business days.
- **RNBD**: Registro Nacional de Bases de Datos (SIC).
- **SIC**: Superintendencia de Industria y Comercio.
- **Suppression list**: addresses never to email (bounces/complaints).
- **Template versioning**: immutable template versions recorded per send.
- **Thymeleaf**: Spring template engine for HTML emails.
- **TikTok Pixel / Events API**: TikTok tracking with server-side dedup.
- **Treble.ai**: Colombian WhatsApp BSP.
- **Twilio / 360dialog / Wati**: global WhatsApp BSPs.
- **UTM**: URL parameters for campaign attribution.
- **Verified purchase**: review linked to a delivered order.
- **Web Push / VAPID**: browser notifications via service worker.
- **Wishlist**: saved products per customer.

## F. Sources

- Meta WhatsApp pricing: https://developers.facebook.com/documentation/business-messaging/whatsapp/pricing · updates: https://developers.facebook.com/docs/whatsapp/pricing/updates-to-pricing · opt-in: https://developers.facebook.com/docs/whatsapp/overview/getting-opt-in · templates: https://developers.facebook.com/docs/whatsapp/business-management-api/message-templates · send: https://developers.facebook.com/docs/whatsapp/cloud-api/guides/send-messages · Colombia rates (third-party mirrors): https://www.plivo.com/whatsapp/pricing/co/ , https://formbeep.com/whatsapp-api-pricing/
- Email: https://postmarkapp.com/pricing · https://resend.com/pricing · https://resend.com/docs/dashboard/domains/regions · https://www.twilio.com/en-us/products/email-api/pricing · https://smtpedia.com/brevo-pricing/ · https://aws.amazon.com/ses/pricing/ · https://docs.aws.amazon.com/general/latest/gr/ses.html · https://www.mailgun.com/pricing/ · Google sender guidelines: https://support.google.com/a/answer/81126
- WhatsApp/SMS providers: https://360dialog.com/pricing · https://www.twilio.com/en-us/whatsapp/pricing · https://www.twilio.com/en-us/sms/pricing/co · https://www.wati.io/pricing/ · https://www.b2chat.io/en/pricing/ · https://www.treble.ai/ · https://www.masiv.com/ · https://www.hablame.co/sms/ · https://www.inalambria.express/post/cuanto-cuesta-enviar-sms-masivos-en-colombia
- Support: https://www.chatwoot.com/pricing · https://www.chatwoot.com/pricing/self-hosted-plans · https://www.chatwoot.com/docs/product/channels/whatsapp/whatsapp-cloud · https://crisp.chat/en/pricing/ · https://www.zendesk.com/pricing/ · https://www.freshworks.com/freshdesk/pricing/ · https://www.freshworks.com/freshdesk/omni/pricing/ · https://www.hubspot.com/pricing/service
- Identity: https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html · https://docs.spring.io/spring-security/reference/servlet/authentication/onetimetoken.html · https://github.com/spring-projects/spring-security/issues/9047 · https://github.com/spring-projects/spring-security/issues/9471 · https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html · https://fidoalliance.org/the-state-of-passkeys-2026-global-consumer-and-workforce-report/ · https://baymard.com/lists/cart-abandonment-rate · https://docs.spring.io/spring-boot/reference/io/email.html
- Analytics/marketing: https://developers.google.com/analytics/devguides/collection/ga4/ecommerce · https://developers.google.com/tag-platform/security/guides/consent · https://cmppartnerprogram.withgoogle.com/ · https://developers.facebook.com/docs/marketing-api/conversions-api/deduplicate-pixel-and-server-events/ · https://ads.tiktok.com/help/article/get-started-pixel · https://support.google.com/merchants/answer/160637 · https://support.google.com/business/answer/7091 · https://developers.google.com/search/docs/appearance/structured-data/review-snippet · https://www.klaviyo.com/pricing · https://www.emailtooltester.com/en/reviews/klaviyo/pricing/ · https://www.omnisend.com/pricing/ · https://mailchimp.com/pricing/marketing/ · https://www.klaviyo.com/blog/abandoned-cart-email · https://www.growthbook.io/pricing · https://posthog.com/pricing
- Marketplaces: https://github.com/api-evangelist/mercado-libre · https://developers.falabella.com/v200.0.0/reference/getting-started · https://www.exito.com/terminos-de-marketplace
- Colombian law: https://www.alcaldiabogota.gov.co/sisjur/normas/Norma1.jsp?i=44306 (Ley 1480) · https://www.alcaldiabogota.gov.co/sisjur/normas/Norma1.jsp?i=49981 (Ley 1581) · https://www.alcaldiabogota.gov.co/sisjur/normas/Norma1.jsp?i=53646 (Decreto 1377)
- Design: https://microservices.io/patterns/data/transactional-outbox.html · https://mjml.io/ · https://react.email/ · https://maizzle.com/ · https://developer.mozilla.org/en-US/docs/Web/API/Push_API

Confidence notes: sic.gov.co, funcionpublica.gov.co and secretariasenado.gov.co refused connections during research, so Ley 1581/1480 texts come from the Alcaldía de Bogotá mirror; the RNBD 100,000-UVT threshold, Ley 2300/2023 scope, Postmark/SendGrid data residency, Zendesk WhatsApp billing and Wati USD prices are from third parties and should be verified before being quoted.
