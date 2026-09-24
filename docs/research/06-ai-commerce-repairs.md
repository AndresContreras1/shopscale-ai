# Research: AI for e-commerce operations, search, support and repairs

> Research notes, September 2026. Model ids and prices were checked on official pages at research time; they change often, re-verify before budgeting. Items marked **verify** could not be confirmed.

## A. Recommended roadmap (value/effort order)

**Phase 0 – Foundations (1–2 sprints, prerequisite for everything)**
- Spring Boot 3.5.x OSS support ended 2026-06-30 (commercial to 2032); Spring AI 1.1.x OSS support also ended 2026-06-30. Spring AI 2.0.x (OSS to 2027-07-31) requires Spring Boot 4.0/4.1 + Framework 7. Plan the Boot 4.1 upgrade now; Java 17 remains the baseline (**verify**).
- Move Gemini usage to the **paid tier**: free-tier prompts are used to improve Google products, may be human-reviewed, and the terms say "Do not submit sensitive, confidential, or personal information to the Unpaid Services". Paid tier: "Google doesn't use your prompts... to improve our products". EEA/UK/CH end users require paid services (relevant for "international later").
- Ship the plumbing: token/cost metrics per feature, feature flags per AI feature, golden datasets + eval runner, AI-disclosure copy, prompt/response logging with PII scrubbing.

**Phase 1 – MVP (high value, low risk, no chat surface)**
1. Hybrid product search + "compatible part for my console" (pgvector HNSW + Postgres Spanish full-text + RRF, compatibility from structured data, not from the LLM).
2. Recommendations v1: rules + co-purchase co-occurrence (pure SQL); embeddings similarity as fallback for cold items.
3. Listing assistant: description/SEO/alt-text drafts and attribute extraction from photos, always human-approved, run through Batch API (50% off).
4. Ops automation v1: keep weekly reports; add purchase-order suggestions (reorder point from moving average of sales + lead time), review sentiment/moderation, order risk rules.

**Phase 2 – Assisted conversations (HITL first)**
5. Repair pre-diagnosis form → probable faults + parts + price range from the shop's price table; technician confirms; AI drafts the customer repair report from the technician's structured notes.
6. Web support assistant with tool calling (catalog, stock, order status by verified order+email) and RAG over policies/FAQ with citations, escalation to a human, injection defenses.

**Phase 3 – Scale**
7. WhatsApp channel (Cloud API), admin copilot (NL→SQL, read-only), fraud scoring v2 (anomaly detection), demand forecasting (exponential smoothing before anything ML), used-console grading from photos (pilot only), competitor price monitoring only from sources whose terms allow it.

## B. Stack decisions

| Decision | Recommendation | Rationale |
|---|---|---|
| **Adopt Spring AI?** | **Yes, with Spring AI 2.0.x after the Boot 4.1 upgrade** (interim: 1.1.8 works on Boot 3.4/3.5 but is OSS-EOL). Keep the `AiClient` interface as a thin façade over `ChatClient` so fallback, caching and the "facts sent" panel stay ours. | 2.0.1 gives ChatClient, `ToolCallingAdvisor` (auto-registered, tool-call limits 40/tool, 150 total), `@Tool`/`ToolContext`/`returnDirect`, `BeanOutputConverter` + provider-native structured output (`useProviderStructuredOutput()`), `StructuredOutputValidationAdvisor` (self-correcting), modular RAG (`RetrievalAugmentationAdvisor`, query rewrite/compression, `DocumentPostProcessor` hook), chat memory (JDBC/Postgres repo; tool messages are not persisted by JDBC), PgVectorStore, MCP client/server starters (STDIO/SSE/Streamable-HTTP), Micrometer metrics (`gen_ai.client.token.usage`, `gen_ai.client.operation`), evaluators (`RelevancyEvaluator`, `FactCheckingEvaluator`). Cons: 2.0 breaking changes (options immutability, Anthropic module now on official SDK, memory needs explicit conversation id), no native hybrid search or reranker, Java-only features lag provider APIs. Hand-written REST keeps only ~2 providers portable; Spring AI gives 4+ (OpenAI, Anthropic, Google GenAI via API key or Vertex, Ollama, Groq via OpenAI base-url). |
| **Primary chat model** | `gemini-3.8-flash` (stable Sept 2026; 1,048,576 in / 65,536 out; text+image+PDF+audio+video; function calling, structured output, caching, batch). Price $0.75/$3.75 per MTok through 2026-12-31, then $1.50/$7.50. | Multimodal (photos for listings/grading), cheapest frontier-class Flash, implicit caching from 4,096 tokens, Batch 50%. |
| **Small/cheap model (classification, moderation, routing, subagents)** | `gemini-3.1-flash-lite` ($0.25/$1.50) or `gemini-3.5-flash-lite` ($0.30/$2.50); cross-vendor fallback `gpt-6-luna` ($0.10 / cached $0.01 / $0.50; 1.05M ctx; function calling needs `reasoning_effort: none` on Chat Completions). | Cost control by routing small→large. |
| **Large model (reports, repair reasoning, SQL generation)** | `gemini-3.1-pro-preview` ($2/$12 ≤200k tokens; no free tier) or `claude-sonnet-5` ($2/$10; 1M ctx; structured outputs GA; cache reads 10%) / `gpt-6-sol` ($2/$10). Avoid `claude-fable-5-1`/`gpt-6-astra` ($10/$50) for store workloads. | Same price band; pick by eval, not by brand. `claude-haiku-4-5` ($1/$5) may retire after 2026-10-15. |
| **Local/open (cost ceiling, privacy)** | Ollama (structured outputs via `format` JSON schema, tool calling, vision) with `gpt-oss`, `qwen3`, `gemma4`; Groq for hosted open models (`llama-3.3-70b-versatile`, `openai/gpt-oss-120b`, `llama-3.1-8b-instant`, 131k ctx; pricing **verify**). | Use for evals, judges, moderation and bulk jobs; not needed at MVP. |
| **Embeddings** | `text-embedding-3-small` ($0.02/MTok, 1536 dims default, **verify** dims) or `gemini-embedding-2` (stable Apr 2026; $0.20/MTok text; 8,192-token input; 128–3072 dims, recommended 768/1536/3072; 100+ languages; multimodal). Do **not** use `text-embedding-004` (shut down 2026-01-14); `gemini-embedding-001` retires 2028-05-14. Open alternative: `BAAI/bge-m3` (1024 dims, 8,192 tokens, MIT) or `intfloat/multilingual-e5-large` (1024 dims, 512 tokens, MIT, needs `query:`/`passage:` prefixes) via Spring AI's ONNX `TransformersEmbeddingModel`. | Spanish coverage; Matryoshka lets you store 768 dims to keep HNSW small. Spring AI's Google GenAI embedding module still documents `text-embedding-004` as default: set the model explicitly and **verify** `gemini-embedding-2` works through it. |
| **Vector store** | pgvector 0.8.6 in PostgreSQL 16 (supports PG 13+), HNSW (`m=16`, `ef_construction=64`, tune `hnsw.ef_search`), `halfvec` to halve storage, iterative scans for filtered queries; hybrid = `to_tsvector('spanish', …)` + vector search fused with RRF; optional cross-encoder rerank. | One database, transactional with catalog; Spring AI `PgVectorStore` (HNSW+cosine default, JSONB metadata filters) covers RAG, custom `JdbcTemplate` query covers hybrid. |
| **Reranker (later)** | Cohere `rerank-v4.0-fast`/`rerank-v4.0-pro` (multilingual, hosted) or self-hosted `BAAI/bge-reranker-v2-m3` (0.6B, Apache-2.0) behind a small HTTP sidecar plugged into `DocumentPostProcessor`. | Spring AI has no reranker abstraction. |
| **Cost levers** | Batch APIs: OpenAI 50% (24h window), Gemini 50% (24h target), Anthropic 50%. Caching: OpenAI cached input 0.1× (≥1,024 tokens), Gemini implicit caching (≥4,096 tokens on 3.x Flash), Anthropic cache reads 0.1× (5-min write 1.25×). | Put the static system prompt + tool schemas first so the prefix caches. |

## C. Per use case design

| Use case | Data | Model | Guardrails | HITL | Eval | Cost control |
|---|---|---|---|---|---|---|
| **a. Hybrid search + compatible part** | Product title/attrs/description embeddings (768 dims), Spanish tsvector, `compatibility(part_id, console_model, generation)` table, synonyms (PS5/PlayStation 5, "control"/"mando") | Embeddings only at query time; optional Flash-Lite for query rewrite; no LLM in ranking path | Compatibility answered from the table, never inferred; LLM may only rewrite/expand queries; filter by stock/status in SQL | None at runtime; catalog team curates compatibility | Golden set of ~200 Spanish queries with expected SKUs; recall@10, MRR; A/B vs keyword-only | Embed once per SKU change; cache query embeddings in Redis; whole catalog ≈1.5M tokens ≈ $0.03–0.30 |
| **b. Recommendations** | Orders (co-purchase pairs), category graph (console→controllers→games→parts), views | Phase 1: SQL co-occurrence + rules; Phase 2: item embeddings similarity | Exclude out-of-stock, repair-only parts, age-rated mismatch; cap price gap | Merchandiser can pin/ban pairs | Offline: hit-rate on held-out baskets; online: attach rate | Zero LLM cost; nightly job |
| **c. Support assistant (web → WhatsApp)** | Read-only tools: `searchCatalog`, `getStock`, `getOrderStatus(orderId, email)`, `getPolicy`; RAG over policies/FAQ/warranty chunks with `metadata.section` for citations | `gemini-3.8-flash` (fallback `gpt-6-luna`); Flash-Lite for intent/injection classification | OWASP LLM01/07: tools whitelisted per request, `ToolContext` carries verified customer id (never from model), tool inputs validated in code, tool-call limits, output schema with `answer`, `citations[]`, `needs_human`; deny actions (no refunds/cancellations), no policy text invented (`allowEmptyContext=false` → "no encontré esto, te paso con un asesor"); rate limit per session; system prompt never echoed | Escalation button always visible; `needs_human` routes to WhatsApp/human inbox; WhatsApp policy: automation allowed in the 24-h window but "prompt, clear, and direct escalation paths" to a human are mandatory; opt-in required for outbound templates | 100-turn golden transcript set; LLM-as-judge (`RelevancyEvaluator`/`FactCheckingEvaluator` + rubric), citation precision, escalation rate, injection red-team suite (30 attacks) run in CI | ≈3k in + 300 out per turn: $0.0034 (3.8 Flash), $0.0012 (3.1 Flash-Lite), $0.00045 (gpt-6-luna) → 10k turns/month ≈ $5–45 before caching; cache static prefix; monthly token budget per channel; WhatsApp service messages inside 24 h are free, templates are per-message and Colombia utility/auth rates rose 2025-10-01 |
| **d. Repair pre-diagnosis + reports** | Symptom taxonomy (console, model, symptom codes), fault→parts mapping, shop price table (min/max per repair), historical tickets (pseudonymized) | Sonnet 5 / 3.1 Pro for reasoning; structured output `{probable_faults[], parts[], price_range, confidence, questions[]}` | Prices only from the table (tool `getRepairPrice`), never generated; every answer labeled "estimado, sujeto a revisión técnica"; refuse safety-critical advice (batteries, PSU) beyond "bring it in" | Technician confirms/edits before the quote is sent; report generated from technician's structured notes, tech approves | 50 labeled real tickets: top-3 fault accuracy, price-range coverage; report rubric (completeness, no invented work) | ~2k in + 600 out ≈ $0.005–0.01 per case on Sonnet 5; batch reports nightly |
| **e. Listing creation + photo attributes + grading** | Product attrs, brand tone guide, photos (≤6), SEO keyword list | `gemini-3.8-flash` multimodal (258 tokens per image ≤384 px, per 768×768 tile otherwise) | Schema-constrained output (title ≤70 chars, bullets, alt text ≤125 chars, `attributes{}` with enum values); no claims not present in attrs; profanity/brand-name check; grading returns evidence per defect + confidence, feasibility pilot only | Editor approves every listing; grading is advisory, technician sets the final grade | Human rating of 50 drafts; attribute extraction F1 vs ground truth; grading agreement (kappa) with technician | Batch API: ≈$0.002–0.004 per listing; 1,000 listings ≈ $2–4 |
| **f. Ops automation** | Sales, stock, lead times, reviews, orders/payments | Reports: Sonnet 5/Flash (existing); moderation: `omni-moderation-latest` (free, text+image) + Flash-Lite sentiment; forecasting/fraud: code, no LLM | Facts computed in code (already done), LLM narrates only; PO suggestions are proposals with reorder math shown; fraud: rules (velocity, address/IP mismatch, high-value first order) + z-score/isolation-forest anomaly score; forecasting: moving average → Holt-Winters in Java; Prophet is Python/R only | Owner approves POs; fraud score only holds orders for manual review; moderation auto-hides only above threshold | Backtest forecasts (MAPE) and fraud rules (precision/recall on chargebacks); report "fact-check" judge | Weekly report ≈ $0.03; moderation free; forecasting zero tokens |
| **g. Admin copilot (NL→SQL)** | Read-only Postgres role over curated views (`v_sales_daily`, `v_stock`), schema docs | Sonnet 5 / 3.1 Pro with structured output `{sql, explanation, assumptions}` | Read-only role, allowlisted views, single `SELECT`, `LIMIT` injected, statement timeout, cost cap via `EXPLAIN`, SQL parsed/validated before execution, no PII columns in views, results rendered with the SQL shown | Admin sees and confirms SQL for non-trivial queries; feedback thumbs feed the golden set | 40 question→SQL pairs, execution accuracy; "facts sent" panel reused | Cache identical questions; ~$0.01/question |

## D. Governance checklist

- Structured outputs everywhere: JSON Schema via `BeanOutputConverter` + provider-native modes (OpenAI `strict:true`; Gemini `response_schema`; Claude `output_config.format`); validate in code, retry once, then rule-based fallback.
- Evals in CI: golden datasets per use case, LLM-as-judge with a cheaper model plus human spot checks; block deploy on regression.
- Red-team prompt injection (direct, via product reviews/RAG docs, via tool results); treat all retrieved text as data; tools least-privilege, read-only, server-verified identity.
- PII: pseudonymize before sending (order ids, hashed emails, no names/addresses); paid tiers only (Google paid: no training; OpenAI API: no training by default, 30-day abuse logs, ZDR by approval; Anthropic: no training by default); comply with Ley 1581/2012 and Ley 1480/2011 duties (**verify** SIC AI guidance).
- Logging/tracing: Micrometer `gen_ai.*` metrics, traces; keep `log-prompt`/`log-completion` off in prod or scrub; retention policy.
- Cost: per-feature token budgets and alerts, Redis caching of deterministic outputs, model routing small→large, batch for offline work, rate limits per user/session, max tool calls, `max_tokens` caps.
- Latency: async jobs for reports/listings/embeddings, streaming for chat, timeouts + circuit breaker to rule-based fallback.
- Feature flags per AI capability; kill switch; model ids pinned and reviewed quarterly (Gemini 2.0 shut down 2026-06-01, 2.5 limited to past users).
- Disclosure: label AI answers and drafts ("Asistente automático"), first-interaction notice, human contact path. EU AI Act Art. 50 applies from 2026-08-02 (inform users they interact with AI; mark synthetic content; Digital Omnibus adjustments **verify**), relevant once EU customers are served. Colombia: no AI law in force; the government bill (PL 043/2025S–324/2025C) was archived June 2026; a new PL 025/2026 Senado was filed July 2026 (**verify** text).
- Competitor scraping: no Colombian anti-scraping statute found; respect site terms (marketplaces usually forbid it), robots.txt, no personal data, prefer official APIs; legal review before automating (**verify**).

## E. Glossary

- **Spring AI ChatClient**: fluent API to call chat models with prompts, tools, advisors.
- **Advisor**: interceptor around a ChatClient call (memory, RAG, safety, logging).
- **ToolCallingAdvisor**: Spring AI 2.0 advisor running the tool-call loop with limits.
- **@Tool / ToolContext / returnDirect**: declare functions for the model; pass hidden context; return tool output directly.
- **Structured output**: model output constrained to a JSON Schema; `BeanOutputConverter` maps it to Java records.
- **StructuredOutputValidationAdvisor**: Spring AI 2.0 advisor that re-prompts on invalid JSON.
- **RAG**: retrieval-augmented generation; answer from retrieved documents.
- **RetrievalAugmentationAdvisor / QuestionAnswerAdvisor**: modular vs naive RAG advisors.
- **DocumentPostProcessor**: RAG hook for reranking/compression.
- **Chat memory**: stored conversation window keyed by conversation id (JDBC repo in Postgres).
- **MCP**: Model Context Protocol; standard for exposing tools/resources to agents.
- **Micrometer observations**: metrics/traces (`gen_ai.client.token.usage`).
- **RelevancyEvaluator / FactCheckingEvaluator**: Spring AI LLM-as-judge evaluators.
- **SafeGuardAdvisor**: basic content-safety advisor.
- **pgvector / HNSW / IVFFlat / halfvec**: Postgres vector extension; graph index; partition index; 16-bit vectors.
- **ef_search / m / ef_construction**: HNSW recall/speed knobs.
- **Iterative scans**: pgvector 0.8 feature fixing filtered ANN recall.
- **Hybrid search / RRF**: combine full-text and vector rankings via reciprocal rank fusion.
- **Reranker / cross-encoder**: model scoring query-document pairs after retrieval.
- **Embeddings / Matryoshka (MRL)**: vector representations; truncatable dimensions.
- **bge-m3, multilingual-e5, bge-reranker-v2-m3**: open multilingual embedding/reranker models.
- **ONNX / TransformersEmbeddingModel**: run embedding models locally in the JVM.
- **Ollama / Groq**: local model runtime; hosted fast inference for open models.
- **Batch API**: async processing at 50% price within ~24 h.
- **Prompt/context caching**: reuse of a repeated prompt prefix at reduced price.
- **Model routing**: cheap model first, escalate on low confidence.
- **Token budget / rate limit / kill switch**: spend and abuse controls.
- **Golden dataset / LLM-as-judge / red-teaming**: fixed test cases; model grading; adversarial tests.
- **Prompt injection / system prompt leakage / excessive agency**: OWASP LLM Top 10 risks.
- **Pseudonymization / ZDR**: replacing identifiers before sending; zero data retention.
- **HITL**: human-in-the-loop approval.
- **Feature flag**: runtime switch per capability.
- **Co-occurrence recommendations**: items frequently bought together.
- **Moving average / exponential smoothing / Holt-Winters / Prophet**: forecasting methods (Prophet: Python/R).
- **Anomaly detection / isolation forest / z-score**: outlier scoring for fraud.
- **NL→SQL**: natural language to SQL with read-only guardrails.
- **WhatsApp Cloud API / 24-h service window / templates**: Meta messaging platform; free service replies; paid outbound messages.
- **EU AI Act Art. 50**: transparency duties (disclose AI, mark synthetic content).
- **Ley 1581/2012, Ley 1480/2011, SIC**: Colombian data-protection and consumer laws; regulator.
- **Vertex AI vs Gemini Developer API**: enterprise GCP endpoint vs API-key endpoint (both via Spring AI Google GenAI).
- **gemini-3.8-flash / 3.1-flash-lite / 3.1-pro-preview / gemini-embedding-2**: current Google model ids.
- **gpt-6-astra / gpt-6-sol / gpt-6-luna / text-embedding-3-small**: current OpenAI ids.
- **claude-fable-5-1 / claude-opus-5-5 / claude-sonnet-5 / claude-haiku-4-5**: current Anthropic ids.
- **omni-moderation-latest**: free OpenAI moderation model.

## F. Sources

- Spring AI docs (2.0.1): https://docs.spring.io/spring-ai/reference/index.html · upgrade notes https://docs.spring.io/spring-ai/reference/upgrade-notes.html · pgvector https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html · MCP https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html · observability https://docs.spring.io/spring-ai/reference/observability/index.html · Google GenAI chat https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html · tools https://docs.spring.io/spring-ai/reference/api/tools.html · RAG https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html · advisors https://docs.spring.io/spring-ai/reference/api/advisors.html · chat memory https://docs.spring.io/spring-ai/reference/api/chat-memory.html · structured output https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html · evaluation https://docs.spring.io/spring-ai/reference/api/testing.html · OpenAI chat https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html · Anthropic chat https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html · Ollama https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html · comparison https://docs.spring.io/spring-ai/reference/api/chat/comparison.html · embeddings https://docs.spring.io/spring-ai/reference/api/embeddings.html · Google embeddings https://docs.spring.io/spring-ai/reference/api/embeddings/google-genai-embeddings-text.html · ONNX https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html
- Spring AI 2.0 GA blog https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/ · support windows https://api.spring.io/projects/spring-ai/generations and https://api.spring.io/projects/spring-boot/generations · releases https://github.com/spring-projects/spring-ai/releases
- Gemini: models https://ai.google.dev/gemini-api/docs/models · 3.8 Flash https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash · 3.1 Flash-Lite https://ai.google.dev/gemini-api/docs/models/gemini-3.1-flash-lite · 3.5 Flash-Lite https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite · embedding-2 https://ai.google.dev/gemini-api/docs/models/gemini-embedding-2 · pricing https://ai.google.dev/gemini-api/docs/pricing · rate limits https://ai.google.dev/gemini-api/docs/rate-limits · embeddings https://ai.google.dev/gemini-api/docs/embeddings · caching https://ai.google.dev/gemini-api/docs/caching · batch https://ai.google.dev/gemini-api/docs/batch-api · deprecations https://ai.google.dev/gemini-api/docs/deprecations · image tokens https://ai.google.dev/gemini-api/docs/image-understanding · structured output https://ai.google.dev/gemini-api/docs/structured-output · terms https://ai.google.dev/gemini-api/terms
- OpenAI: pricing https://developers.openai.com/api/docs/pricing · models https://developers.openai.com/api/docs/models · gpt-6-luna https://developers.openai.com/api/docs/models/gpt-6-luna · batch https://developers.openai.com/api/docs/guides/batch · prompt caching https://developers.openai.com/api/docs/guides/prompt-caching · structured outputs https://developers.openai.com/api/docs/guides/structured-outputs · moderation https://developers.openai.com/api/docs/guides/moderation · data use https://developers.openai.com/api/docs/guides/your-data
- Anthropic: models https://platform.claude.com/docs/en/docs/about-claude/models/overview · pricing https://platform.claude.com/docs/en/docs/about-claude/pricing · structured outputs https://platform.claude.com/docs/en/build-with-claude/structured-outputs · training policy https://privacy.claude.com/en/articles/7996868-is-my-data-used-for-model-training
- pgvector https://github.com/pgvector/pgvector · Groq models https://console.groq.com/docs/models · Ollama structured outputs https://docs.ollama.com/capabilities/structured-outputs · bge-m3 https://huggingface.co/BAAI/bge-m3 · bge-reranker-v2-m3 https://huggingface.co/BAAI/bge-reranker-v2-m3 · multilingual-e5-large https://huggingface.co/intfloat/multilingual-e5-large · Cohere rerank https://docs.cohere.com/docs/rerank · Prophet https://facebook.github.io/prophet/ · OWASP LLM Top 10 https://genai.owasp.org/llm-top-10/
- WhatsApp pricing https://developers.facebook.com/docs/whatsapp/pricing · messaging policy https://whatsappbusiness.com/es-la/policy/
- EU AI Act: https://digital-strategy.ec.europa.eu/en/policies/regulatory-framework-ai · Article 50 https://www.artificial-intelligence-act.com/Artificial_Intelligence_Act_Article_50.html
- Colombia AI bills: https://algoritmos.uniandes.edu.co/proyecto-de-ley-por-medio-del-cual-se-regula-la-inteligencia-artificial-en-colombia-para-garantizar-su-desarrollo-etico-responsable-competitivo-e-innovador-y-se-dictan-otras-disposiciones/ · https://www.camara.gov.co/inteligencia-artificial/ · https://minciencias.gov.co/sala_de_prensa/minciencias-lidera-el-proyecto-ley-que-busca-regular-el-desarrollo-etico-seguro-y · https://telecomunicaciones.uexternado.edu.co/implementacion-de-la-ia-en-colombia-analisis-al-proyecto-de-ley/ · https://www.camara.gov.co/wp-content/uploads/2026/07/proyectos-ley/documentos/proyecto-36127/P.L.025-2026SC-INTELIGENCIA-ARTIFICIAL.pdf

Unverified at research time: Groq per-token prices, SIC guidance on AI and personal data, Ley 2300/2023 contact rules, exact Digital Omnibus changes to Art. 50, `text-embedding-3-small` dimension parameter.
