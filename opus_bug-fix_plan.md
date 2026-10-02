# Repository Analysis & Safe Implementation Plan — Adi-Vritti (SIH26 PS 26238)

**Date:** 2026-10-02
**Repo:** `sunnydev07/sih26-adi-vritti` @ `d93fc34` (main)
**Mode:** Read-only analysis — no source modifications
**Scope:** Architecture map, dependency graph, bug inventory, safest implementation approach

---

## 1. Goal & Non-Negotiables

**Goal:** Provide an implementation-ready plan any agent can execute without violating project invariants.

**Non-negotiables (from `Agent.md` + `docs/specs/00-overview.md`):**
- `docs/openapi/core.yaml` is the contract — no endpoint/schema drift without regenerating `packages/api-client`.
- No mock outside seam: `apps/officer-web/src/lib/api.ts` and `apps/mobile/src/lib/jago.ts` only.
- No PII in logs/UI, Aadhaar as `AVR1:<keyId>:<ref>` only, paise not rupees, ISO8601 with zone.
- LLM never free-generates status/provenance; deficiency never blocks; LLM outputs are advisory.
- Java 21 / Spring Boot 3.3.5 records (no Lombok), Python 3.13 FastAPI + Pydantic v2 + ruff 100, TS strict `no any`.

---

## 2. Architecture Map

### 2.1 Monorepo Layout

```
OPUS_PLAN_238/
├── apps/mobile          # Expo SDK 52, React 18.3.1, expo-router 4, drizzle-orm + expo-sqlite
├── apps/officer-web     # Next.js 16.3.6, React 19.2.8, Tailwind 4, framer-motion, recharts
├── services/core        # Spring Boot 3.3.5 / Java 21 — authoritative persistence & business logic
├── services/ai          # FastAPI / Python 3.13 — matching, gap-hash, JAGO, DocAI, RAG, JEV/Groq
├── services/govsim      # Express 4.19.2 / Node 22 — 7 hostile gov-system mocks + chaos (8%/10%/2%/20RPM)
├── packages/rules       # JSON rule-sets per scheme (2026-27: pre/post-matric, top-class, nfst, nos) + schema.json
├── packages/ui-tokens   # tokens.json + assistant_faq.json (mirrored into mobile/vendor)
├── packages/api-client  # Generated TS-fetch + Java-restclient from core.yaml (gitignored, `make api`)
├── infra/               # docker-compose.yml (pgvector:pg16 + redis:7 + govsim/core/ai), init-db.sql
├── data/synthetic/      # generate.py (seed 26238) → output/*.csv, seed.sql (gitignored)
├── docs/openapi/core.yaml  # 921 lines, 13 paths, 22 schemas — THE contract
└── docs/specs/, docs/adr/, docs/demo/, docs/superpowers/plans/
```

### 2.2 The 9 Layers (00-overview.md)

| # | Layer | Owner | Key Files |
|---|-------|-------|-----------|
| 1 | USID (Unified Scholar ID) | core/identity | `UsidResolver.java`, `IdentityService.java`, `Scholar.java` |
| 2 | Verified Claims Wallet | core/verification | `VerificationOrchestrator.java`, `ClaimsService.java`, `Claim.java` |
| 3 | Verification Orchestrator | core/verification | `VerificationOrchestrator.java` + 4 strategies + 7 adapters (`GovsimClient`, `NspAdapter` etc.) |
| 4 | Eligibility & Conflict Engine | core/eligibility | `RuleEngine.java`, `EligibilityService.java`, `SchemeRuleVersion.java` |
| 5 | SLA & Deficiency Loop | core | `SlaCalculator.java`, `ApplicationService.java`, `Deficiency` |
| 6 | DBT Failure Doctor | core + ai | `Disbursement`, `JagoToolRouter`, `services/groq_service.py` (dbt-explain) |
| 7 | JAGO+ Skill | core + ai + mobile/web | `JagoController.java`, `JagoToolRouter.java`, `services/jago_service.py`, `apps/mobile/src/lib/jago.ts` |
| 8 | Coverage Gap Engine | core + ai | `CoverageGapService.java`, `services/gap_service.py` (`/gap/hash` HMAC) |
| 9 | DPDP Consent | core/consent | `ConsentGate.java`, `ConsentService.java`, `ConsentArtefact.java`, `AccessAudit.java` |

### 2.3 Runtime Topology (infra/docker-compose.yml)

```
Browser/Mobile → officer-web (Next BFF :3000) ─┬─→ core (:8080 /v1/*, JWT, Flyway V1→V2)
               → mobile (Expo web :19006)     │         ├─→ postgres:5432 (pgvector:pg16 + vector/pgcrypto)
               └─→ ai (:8000 /health /match /gap /jago /docai /decisions) ←┘         ├─→ redis:7
                        │                    └─→ govsim (:4000 /nsp /sfmp /nos /udise /pfms /ugc-nta /digilocker)
                        └─→ govsim (chaos) + opencode.ai/zen + api.groq.com
```

CI (` .github/workflows/ci.yml`, 7 jobs, 30m timeout): `migration` → `core` (gradlew build) → `ai` (pytest) → `api-client` (generate + tsc strict) → `govsim` → `token-mirrors` → `stack` (compose up, flyway vectors, actuator UP, contract-drift check, AI auth gate 401/200).

---

## 3. Dependency Graph

### 3.1 Internal

```
docs/openapi/core.yaml
  └─→ packages/api-client (TS + Java) ─→ apps/officer-web, apps/mobile (regenerated via `make api`)
packages/rules/*.json ─→ RuleValidator (networknt) ─→ RuleBootstrapRunner ─→ SchemeRuleVersion (DB)
packages/ui-tokens/tokens.json ─→ apps/officer-web + apps/mobile/vendor/ui-tokens (checked by check-token-mirrors.mjs)
data/synthetic/generate.py (seed 26238) ─→ data/synthetic/output/seed.sql ─→ postgres (make seed)
services/core ─→ postgres (JPA/Flyway), redis (CacheConfig), govsim (WebClient), ai (x-ai-service-token)
services/ai ─→ core (JagoToolRouter proxy, 10s timeout), govsim (indirect), opencode/groq (httpx 3s/10s)
apps/officer-web BFF ─→ core (/v1/*) + ai (/decisions/stp, /jago/help, 2500ms abort)
apps/mobile ─→ core (direct) + ai BFF (EXPO_PUBLIC_ASSISTANT_BFF_URL) + offline outbox (expo-sqlite/drizzle, currently stub)
```

### 3.2 External

- **LLM:** `opencode.ai/zen/v1/systemone` (jev-1.13-free) 3s; `api.groq.com/openai/v1/chat/completions` (openai/gpt-oss-20b) 10s — both behind `enable_jev`/`enable_groq` flags.
- **DB:** `pgvector/pgvector:pg16` required (vector extension); `redis:7-alpine`.
- **Auth:** Spring Security OAuth2 resource-server (JWT/JWKS or HS256 dev fallback); AI `x-ai-service-token` / `Authorization: Bearer` with `hmac.compare_digest`.
- **Deploy:** Vercel (officer-web standalone), Docker host (core/ai/govsim), EAS (mobile).

### 3.3 Version Pins (critical)

- Node `22.12.0`, Java toolchain 21 (override 23), Python `>=3.13`, Expo SDK 52 (kotlinVersion `1.9.25`), Next 16.3.6, Spring Boot 3.3.5, openapi-generator-cli 7.7.0, pgvector pg16, resilience4j 2.2.0, json-schema-validator 1.5.9.

---

## 4. Data Flow (Happy Path)

1. **Identity:** `POST /v1/admin/identity/resolve` → `UsidResolver` (Jaro-Winkler, 0.60 floor / 0.90 auto-accept, gap hash HMAC-SHA256) → `Scholar` + `ScholarSystemLink` + `IdentityResolution`.
2. **Application:** `POST /v1/applications` (mobile) → `Application` (stage/currentActor/slaDeadline via `SlaCalculator`) → `ApplicationEvent` timeline.
3. **Verification:** `POST /v1/verify` → `VerificationOrchestrator` (idempotencyKey per usid, 4 strategies: authoritative/corroboration/docAI/manual, 7 adapters with chaos) → `Claim` (AES-GCM value, 365d validUntil, evidenceRef) — `@Cacheable` key includes evidenceRef, `@Transactional` commit.
4. **Consent:** Every personal-data read → `ConsentGate` (checks `ConsentArtefact` purpose/expiry, minor→guardian, logs `AccessAudit`) — `GET /v1/scholars/{usid}/claims|dashboard|disbursements` are consent-gated (403 if missing).
5. **Eligibility:** `POST /v1/eligibility/evaluate` → `RuleEngine` (operators `in|lte|gte|eq|exists`) over `SchemeRuleVersion.rulesJson` + `compatibility-matrix.json` (max_merit 1, PRE/POST mutual exclusive) → verdicts per scheme.
6. **JAGO:** Mobile `ChatFab` / officer `JagoController` → `JagoToolRouter` → AI `POST /jago/tool/{name}` (proxies to core with 10s timeout, generic `CLIENT_DETAIL` on 4xx/5xx to avoid USID oracle) + `help_service` deterministic FAQ + `rag_service` Groq synthesis fallback.
7. **Gap:** `POST /gap/hash` (HMAC) + `GET /v1/admin/coverage-gap` (Core) — gap query stub in AI returns "not implemented".
8. **Disbursement/DBT:** `GET /v1/disbursements/{usid}` + `POST /decisions/dbt-explain` (Groq) + `POST /decisions/fraud-screen` (JEV).
9. **Officer review:** `GET /v1/admin/exceptions` (breach-risk ordering via `StpScoreCalculator`) → SLA dashboard, no approve endpoint (demo-path: enrolled/applicants 0).

---

## 5. Bug Inventory (Prioritized)

### 5.1 Critical — Security / Fail-Open

| ID | Location | Issue | Impact |
|----|----------|-------|--------|
| C1 | `.env` (root, gitignored but on disk) | Live `GROQ_API_KEY=gsk_gU0...`, `OPENCODE_ZEN_API_KEY=oc_sk_4c97...` present; `services/ai/app/config.py:105` auto-loads `.env`. Baked image ships secrets. | Key compromise, log/env dump blast radius. Rotate immediately. |
| C2 | `infra/docker-compose.yml:127`, `services/core/src/main/resources/application-dev.yml`, `services/ai/app/config.py` (DEV_SALT/DEV_TOKEN) | Dev secrets are defaults (`adivritti_dev`, `dev-salt-rotate-in-prod`, `dev-only-ai-service-token`, base64 JWT `ZGV2...`). `allow-insecure-dev=true` → `SecurityConfig.filterChain()` does `anyRequest().permitAll()`. No prod fail-fast if value equals dev constant (except AI token gate + JWT length check). | Deploy with `SPRING_PROFILES_ACTIVE!=prod` leaves Core+AI wide open, no boot failure. |
| C3 | `apps/officer-web/src/proxy.ts:22`, `src/app/api/session/route.ts:123` | Proxy checks only `cookie.value` presence, not signature/expiry/JWT. `demo:${phone}` predictable, attacker sets `adivritti_session=anything` via devtools. `POST /api/session` accepts any phone≥10 + OTP≥4, no SMS, mints demo cookie if `NODE_ENV!=production`. Public Vercel URL is reachable. | Auth bypass on officer console. |
| C4 | `services/ai/app/config.py:105` `extra="ignore"` | Misspelled env vars silently dropped, masking deployment misconfig. | Silent prod misconfig (e.g., `GAP_HMAC_SALT` typo → 503 gap/hash). |
| C5 | `services/core/src/main/java/in/adivritti/core/config/SecurityConfig.java:77` | `permitAll` on `/v3/api-docs` exposes full route map (including `/gap/hash` raw Aadhaar) to unauthenticated scanners. | Recon. |

### 5.2 High — Validation / Race / N+1

| ID | Location | Issue | Impact |
|----|----------|-------|--------|
| H1 | `services/core/.../VerificationOrchestrator.java:84` | `@Cacheable` before `@Transactional` commit; concurrent same `idempotencyKey` races. `persistClaimIdempotent` catches `DataIntegrityViolationException` but relies on DB unique `(usid, idempotencyKey)` — unverified. Empty prover trail on replay loses audit. | Duplicate claims or lost provenance. |
| H2 | `services/core/.../admin/AdminController.java:99` | `applications.findAll()` without pagination (mock 18k rows) — OOM/N+1; paginated path at `:125` exists but default is unpaged. | OOM in prod. |
| H3 | `services/core/.../eligibility/EligibilityService.java:191` | `ruleCache` (ConcurrentHashMap) `invalidateRuleCache()` clears without sync vs concurrent `loadRules` → lost update, stale empty list. | Fail-closed vs stale rules. |
| H4 | `services/core/.../consent/ConsentGate.java:94` | `isMinor` returns `false` on unparseable DOB (fail open); `Period.between(..., LocalDate.now(UTC))` non-deterministic. | Minor bypasses guardian check; flaky tests. |
| H5 | `services/ai/app/routers/docai.py:21` | `claim_type` query param no `Field(max_length)` — arbitrary string persisted as `fields.claim_type`. | Injection / storage bloat. |
| H6 | `services/ai/app/routers/decisions.py:107` | `POST /decisions/dbt-explain` takes `dict[str,Any]` without Pydantic bounds; unbounded `failure_code/details` to Groq prompt. | Prompt injection / token blowup. |
| H7 | `services/ai/app/services/fraud_service.py:132` | `except Exception` on `jev_service.decide` swallows `KeyboardInterrupt`/`SystemExit`. | Masks infra failures as silent fallback. |
| H8 | `services/ai/app/services/jev_service.py:234` | `confidence<0.40` fallback sets `fb["fallback"]=False` overwriting `_fallback_intent`'s `True`, mislabels fallback as live. | Incorrect provenance. |
| H9 | `apps/officer-web/src/lib/api.ts:100` | Empty `catch {}` on `evaluateStpWithJev`; `clearTimeout` may be skipped if fetch throws before assignment → timeout leak. | Hidden network errors, leaked timers. |

### 5.3 Medium — DoS / Leakage / Robustness

| ID | Location | Issue |
|----|----------|-------|
| M1 | `services/govsim/src/index.js:68` | Global `hits` Map per IP filtered per request, never expires beyond 60s window, unbounded under IP churn; chaos truncated JSON intentionally — Core adapters must handle `ValueError` on `r.json()`. |
| M2 | `services/ai/app/services/docai_service.py:172` | `fields["raw_text"]=text[:2000]` leaks PII slice in response body/logs. |
| M3 | `apps/mobile/src/lib/store.ts:28` | `id: obx-${Date.now()}` collision on rapid taps (same ms); `deltaSync` stub returns `{pushed:0}`. |
| M4 | `apps/mobile/src/lib/assistant.ts:171` | `EXPO_PUBLIC_ASSISTANT_BFF_URL` client env — attacker can point app to malicious BFF; no cert pinning. |
| M5 | `apps/officer-web/src/lib/ai-upstream.ts:35` | BFF `2500ms` abort vs JEV `3000ms` — upstream orphaned after BFF timeout. |
| M6 | `services/ai/app/services/groq_service.py:80` | `raise GroqUnavailableError(f"...{exc}")` may leak traceback/headers; logger truncates to 200 chars but propagation not truncated. |

### 5.4 Low — Logic / Consistency

- `services/ai/app/services/help_service.py:44` `STATUS_MARKERS` includes bare `"my"` → false-positive deflection; `HINDI_MARKERS` misroutes English with Hindi loanwords.
- `services/ai/app/services/rag_service.py:213` substring `or` matches `foreign` inflating token scores.
- `services/ai/.../AadhaarVault.java:129` `isReferenceKey` checks prefix only — forged `AVR1:v1:00` passes (not used as gate today).

### 5.5 Already Remediated (per `big-fix_plan.md`, 2026-10-01)

P0: double `anyRequest` → fixed; snake_case → `SNAKE_CASE`; Redis `ZonedDateTime` → `Instant`. P1: 4 unguarded endpoints, identity 0.0 linking, valueless claims, DPDP never invoked — marked done with 19 suites/126 tests. Verify before assuming closed.

---

## 6. Safest Implementation Approach

### 6.1 Principles

1. **Contract-first:** Any `core.yaml` change → `make api` + `tsc --noEmit` + `check-contract-drift.mjs` must pass before merge.
2. **Fail-closed > fail-open:** Every new personal-data read must pass `ConsentGate`; every new AI route must pass `require_service_token`; prod must refuse to boot on dev secrets.
3. **No PII widening:** Never log `aadhaar_ref`, `raw_text`, or decrypted `valueEncrypted`; use `maskAadhaar` / `AadhaarVault.masked`.
4. **One workstream per branch** (Agent.md), conventional commits, demo-honest copy (`src/__mocks__/data.ts` labeled as demo).
5. **Deterministic verification:** Seed `26238` for synthetic data; `make clean` required after pgvector upgrade (README gotcha).

### 6.2 Sequencing (Do Not Reorder Without Justification)

**Phase 0 — Harden secrets & prod guard (no feature work until done):**
- Rotate `GROQ_API_KEY` + `OPENCODE_ZEN_API_KEY` via dashboards; remove `.env` from any baked image (use env injection). Add prod boot check: if `AI_SERVICE_TOKEN` / `GAP_HMAC_SALT` / `AADHAAR_VAULT_HMAC_KEY` / `JWT_SECRET` equals dev constant → fail fast with actionable error.
- Change `config.py: extra="ignore"` → `extra="forbid"` (or at least `warn`) so misspelled env vars surface.
- Scope `SecurityConfig` `permitAll`: restrict `/v3/api-docs` to `dev` profile or `OFFICER` role; keep only `/actuator/health` + `/actuator/info` open.

**Phase 1 — Auth & consent correctness:**
- Replace officer-web demo cookie with signed `httpOnly` JWT (HS256, 8h expiry, `sameSite=lax`, `secure` in prod) or integrate real `POST /v1/auth/officer/login` (currently 404 in core). Make `proxy.ts` verify signature + expiry, not presence. Gate demo mint behind explicit `OFFICER_CONSOLE_DEMO=1` and disable in prod.
- Fix `ConsentGate.isMinor` → fail closed on unparseable DOB (treat as minor or reject), inject `Clock` for deterministic `Period.between`.
- Audit all `GET /v1/scholars/{usid}/*` handlers for `ConsentGate` + `ScholarAccessGuard` coverage; add `EndpointAuthorisationTest` for any new route.

**Phase 2 — Persistence & concurrency:**
- Add DB unique constraint `(usid, idempotency_key)` if missing; verify via `RepositoryQueryValidationTest`. Make `VerificationOrchestrator` cache *after* commit (e.g., `@CachePut` on success or manual `CacheManager` put post-`save`), or remove `@Cacheable` from transactional method. Ensure replay returns persisted claim with prover trail.
- Paginate `AdminController` default path; add `Pageable` + `max pageSize` guard.
- Synchronize `EligibilityService.ruleCache` invalidation (e.g., `synchronized` block or `AtomicReference<Map>` swap) or use `Caffeine` with `refreshAfterWrite`.

**Phase 3 — Input validation & error handling:**
- Add Pydantic bounds: `claim_type` `Field(max_length=64, pattern=...)`, `dbt-explain` schema with `failure_code`/`details` length limits. Enforce `DOCAI_MAX_UPLOAD_BYTES` (5MB default, cap 50MB) already present — add `claim_type` enum check.
- Narrow `fraud_service` `except Exception` → `except (JevUnavailableError, httpx.HTTPError)`.
- Fix `jev_service` fallback flag overwrite (`fb["fallback"]=True`) and preserve live latency separately.
- Fix `officer-web/src/lib/api.ts` `try/finally` `clearTimeout` and surface network errors (no empty `catch`).

**Phase 4 — Leakage & DoS hardening:**
- Redact `docai_service` `raw_text` from response (return only extracted `fields` + `tamper_signals`); truncate logs.
- Fix `store.ts` outbox id: `id: obx-${Date.now()}-${Math.random().toString(36).slice(2,8)}` or `crypto.randomUUID()`.
- Expire `govsim` `hits` Map entries (e.g., `setTimeout` or LRU with TTL) and ensure adapters handle truncated JSON (already chaos-tested, verify `GovsimClient` retry handles `ValueError`).
- Truncate `groq_service` exception propagation; ensure upstream timeouts align (BFF 2500ms vs JEV 3000ms — make BFF ≥ upstream + buffer or cancel upstream).

**Phase 5 — Low-severity polish:**
- Tighten `help_service` `STATUS_MARKERS` (remove bare `"my"`), fix `rag_service` token scoring to word-boundary match, tighten `isReferenceKey` regex to `^AVR1:[a-z0-9_-]+:[A-F0-9]+$`.

### 6.3 What NOT To Do

- Do not add `anyRequest().permitAll()` or `ALLOW_INSECURE_DEV=true` in prod.
- Do not log `aadhaar_ref`, `gap/hash` inputs, or `valueEncrypted` plaintext.
- Do not add `POST /v1/auth/officer/login` mock outside `officer-web/src/app/api/session/route.ts` seam.
- Do not widen `DOCAI_MAX_UPLOAD_BYTES` beyond 50MB without infra review.
- Do not bypass `ConsentGate` for "internal" reads — every personal-data read is consent-gated.

---

## 7. Task Breakdown (Tracer-Bullet Order)

Each task declares blocking edges; execute in order, one branch per task.

| # | Task | Blocks | Validation |
|---|------|--------|------------|
| 0.1 | Rotate leaked keys (`GROQ_API_KEY`, `OPENCODE_ZEN_API_KEY`), ensure `.env` not baked into images | 0.2, all | Keys rotated, `docker build` context excludes `.env` |
| 0.2 | Prod fail-fast on dev secrets + `extra="forbid"` for AI config | 1.1 | Boot fails with dev constant, misspelled env var raises |
| 0.3 | Scope `/v3/api-docs` `permitAll` to dev/role | 1.1 | Unauthenticated `/v3/api-docs` → 401 in prod profile |
| 1.1 | Officer-web signed session (JWT) + fix `proxy.ts` verification | 2.1 | `adivritti_session` tamper → 302 /login, expiry enforced |
| 1.2 | `ConsentGate` fail-closed + inject `Clock` | 2.1 | Unparseable DOB → treated as minor, tests deterministic |
| 1.3 | Authorisation audit for all scholar-scoped routes | 2.1 | `EndpointAuthorisationTest` green for new routes |
| 2.1 | Idempotency constraint + cache-after-commit in `VerificationOrchestrator` | 3.1 | Concurrent same key → one row, replay returns provenance |
| 2.2 | Paginate `AdminController.findAll` + max pageSize | 3.1 | `GET /v1/admin/exceptions?page=0&size=50` paginated, OOM gone |
| 2.3 | Sync `ruleCache` invalidation | 3.1 | Concurrent invalidate+load race test passes |
| 3.1 | Pydantic bounds (`claim_type`, `dbt-explain`) | 4.1 | Oversize/invalid payload → 422, not 500 |
| 3.2 | Narrow `fraud_service` exception + fix JEV fallback flag | 4.1 | `KeyboardInterrupt` not swallowed, fallback labeled correctly |
| 3.3 | Fix `api.ts` `try/finally` + surface errors | 4.1 | Timeout cleared on throw, network error propagated |
| 4.1 | Redact `raw_text` PII + fix outbox id + expire govsim hits | 5.1 | No PII in response/logs, no id collision, hits bounded |
| 4.2 | Align BFF/upstream timeouts + truncate Groq error propagation | 5.1 | No orphaned upstream, no header leak |
| 5.1 | Polish: help markers, rag scoring, reference-key regex | — | Unit tests for each |

---

## 8. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Secret rotation breaks running deployments | Rotate via dashboard, then rolling restart with new env injection; keep old key revoked only after deploy verified (`/health` + `/actuator/health` UP). |
| Prod fail-fast blocks staging that intentionally uses dev profile | Gate check on `SPRING_PROFILES_ACTIVE=prod` only; staging keeps `dev` but documents risk. |
| Cache-after-commit changes verification latency | Measure `VerificationOrchestrator` p95 before/after; cache only on success, not on exception. |
| Pagination breaks officer-web dashboard (mocks 18k) | Keep `findAll` for `OFFICER_CONSOLE_DEMO` mock path, paginate real DB path; add `X-Total-Count` header. |
| Pydantic `extra="forbid"` breaks existing deploys with extra env vars | Audit `infra/docker-compose.yml` + `application-dev.yml` + Render/Vercel env before flipping; allow `warn` first. |
| JEV/Groq timeouts cause flaky CI | Keep `govsim` chaos 8% but use `infra/docker-compose.override.yml` (chaos disabled) for CI stack job; BFF 2500ms → 4000ms if upstream is 3000ms. |

---

## 9. Validation Plan

**Pre-merge (every PR):**
```bash
make test-core   # ./gradlew test -PjavaToolchainVersion=21 — must include EndpointAuthorisationTest, VerificationAttemptTest, etc.
make test-ai     # pytest — must include test_security, test_matching_parity, test_jago, test_docai
make api         # regenerate api-client, then tsc --noEmit in officer-web + mobile
node scripts/check-token-mirrors.mjs
node scripts/check-contract-drift.mjs  # via ci.yml js-yaml step
```

**Stack smoke (infra/docker-compose.yml):**
```bash
make dev-detach && make init-db  # pgvector vector extension, Flyway V1→V2 idempotence
curl -f http://localhost:8080/actuator/health  # UP
curl -f http://localhost:8000/health
curl -f http://localhost:4000/health
# Auth gate: unauthenticated /gap/hash → 401, authenticated → 200/503 (if salt blank)
# Contract: officer-web BFF /api/decisions/stp with/without session cookie
make down
```

**Manual demo-path (docs/specs/demo-path.md, 6 beats):**
1. Coverage gap (empty state 403) → 2. USID resolve (deterministic per salt) → 3. Verify once (idempotent, provenance immutable) → 4. JAGO + DBT explain → 5. Gap + exceptions (breach-risk ordering) → 6. Readiness (rules hot-reload via `APP_RULES_WATCH_INTERVAL_MS 2000` in override).

---

## 10. Open Questions

1. **Officer auth source of truth:** Should core implement `POST /v1/auth/officer/login` (JWT issuer) or will officer-web remain BFF-only with demo cookie? Decision blocks Phase 1.1 — current BFF tries core login then falls back to demo; core has no such endpoint.
2. **AI auth in prod:** Is `ALLOW_INSECURE_DEV` ever `true` in any deployed env? If yes, Phase 0 must make it `false` by default and require explicit `AI_SERVICE_TOKEN`.
3. **Mobile offline outbox:** Is `expo-sqlite`+`drizzle-orm` intended to be fully offline-capable or stub-only for demo? Determines whether `store.ts` collision fix needs full `deltaSync` implementation.

---

## 11. Appendix — Key File Map

- **Contract:** `docs/openapi/core.yaml:1` (921 lines, bearerAuth JWT, 13 paths, ErrorResponse requires `error_code,message,details,at,path`)
- **Security:** `services/core/src/main/java/in/adivritti/core/config/SecurityConfig.java:1`, `services/ai/app/security.py:1`, `services/ai/app/config.py:1`, `apps/officer-web/src/proxy.ts:1`
- **Consent/Privacy:** `services/core/.../consent/ConsentGate.java:1`, `ConsentService.java`, `common/util/AadhaarVault.java:129`, `ClaimValueCipher.java`
- **Verification:** `services/core/.../verification/VerificationOrchestrator.java:84`, `GovsimClient.java`, adapters `NspAdapter`, `SfmpAdapter`, `NosAdapter`, `PfmsAdapter`, `UgcNtaAdapter`, `UdiseAdapter`, `DigiLockerAdapter`
- **Eligibility:** `services/core/.../eligibility/RuleEngine.java`, `EligibilityService.java:191`, `packages/rules/schema.json`, `compatibility-matrix.json`
- **AI:** `services/ai/app/main.py:1`, `routers/{matching,gap,jago,docai,decisions}.py`, `services/{matching,gap,docai,fraud,jev,groq,rag,help}_service.py`
- **Officer-web BFF:** `apps/officer-web/src/app/api/session/route.ts:53`, `src/app/api/decisions/stp/route.ts`, `src/lib/api.ts:100`, `src/lib/ai-upstream.ts:35`
- **Mobile:** `apps/mobile/src/lib/store.ts:28`, `src/lib/jago.ts`, `src/lib/assistant.ts:171`, `src/app/_layout.tsx`
- **Infra:** `infra/docker-compose.yml:127`, `infra/docker-compose.override.yml`, `infra/init-db.sql`, `render.yaml`, `Makefile:1`
- **CI/Guards:** `.github/workflows/ci.yml:1` (299 lines), `scripts/check-token-mirrors.mjs:1`, `scripts/check-contract-drift.mjs:1`
