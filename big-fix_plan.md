# big-fix_plan.md — Adi-Vritti: full-codebase audit, bugs and fix plan

> Audit of `OPUS_PLAN_238` (SIH26 · PS 26238), produced 2026-10-01.
> Read-only: no source file was modified by the audit itself.
>
> **Method.** Full source reading of the tracked repo (~274 files). Framework behaviour
> was verified against the actual Spring Security 6.3.4 and Spring Data Redis 3.3.5
> sources in the local Gradle cache, not from memory.
>
> **Dynamic checks actually run:** in-memory AST/JSON parse of every tracked `.py` (32)
> and `.json` (29) file — zero errors; `node --check` on both govsim entry points —
> clean; byte-compare of the `ui-tokens` mirrors — identical; lockfile version
> inspection. Existing Gradle results (`services/core/build/test-results/test`,
> 13 suites / 105 tests / 0 failures, dated 2026-09-30) are historical and, critically,
> never boot a Spring context — which is exactly why P0-1 survives them.
>
> **Not run:** Gradle build (local JDK 23 vs project toolchain 21), pytest (project
> requires Python >= 3.13, only 3.10 available locally), Docker/compose.

## Architecture (verified)

```text
apps/officer-web  Next.js 16 console   -> src/lib/api.ts -> __mocks__/   (no live Core calls)
apps/mobile       Expo SDK 52          -> lib/jago.ts mocks + lib/assistant.ts offline FAQ mirror
services/core     Spring Boot 3.3.5 / Java 21 -> Postgres(pgvector) + Redis + Flyway V1 (12 tables)
services/ai       FastAPI              -> /match /gap /jago /docai /rag /decisions (Groq + JEV)
services/govsim   Express chaos mocks  -> 7 "government systems"
packages/rules    versioned JSON rules (read from disk at request time; schema.json never enforced)
packages/ui-tokens shared tokens + FAQ mirror (byte-identical to the AI canonical copy today)
```

---

## P0 — blockers

### P0-1 · The dev stack cannot boot Core: `anyRequest()` is configured twice

`services/core/src/main/java/in/adivritti/core/config/SecurityConfig.java:66-78` calls
`.anyRequest().authenticated()` (line 69) and then, when `allow-insecure-dev=true`
(the dev-profile default used by `make dev` and `infra/docker-compose.yml`), calls
`.anyRequest().permitAll()` (line 76) on the same registry.

Spring Security's `AbstractRequestMatcherRegistry.anyRequest()` asserts
`!anyRequestConfigured` with the message *"Can't configure anyRequest after itself"*,
and `HttpSecurity.authorizeHttpRequests(customizer)` applies the lambda immediately to
the existing registry (`getOrApply(...).getRegistry()`; the registry instance is created
once in the configurer's constructor). **Result: `IllegalStateException` during context
refresh → Core fails to start in the dev profile**, i.e. the exact configuration used by
the judging demo and by the CI `stack` job. Production (`allow-insecure-dev=false`) is
unaffected.

**Fix.** One `authorizeHttpRequests` block whose contents branch on the flag — never call
`anyRequest()` twice. Add a `@SpringBootTest` context-load test with
`@ActiveProfiles("dev")` so this class of bug fails in unit tests instead of the
compose job.

### P0-2 · The OpenAPI contract does not match the runtime wire format

- No JSON naming strategy anywhere (grep: no `@JsonNaming`, no `PropertyNamingStrategies`,
  no `@JsonProperty`), yet `docs/openapi/core.yaml` documents snake_case
  (`claim_id`, `valid_until`, `provenance`, …). Every Java record serialises camelCase,
  so clients generated from this contract cannot deserialise real responses.
  `packages/api-client` has in fact never been generated — only `generate.sh` exists.
- Query parameters are camelCase in code but snake_case in the contract:
  `pvtgFilter` (`AdminController.java:52`) vs `pvtg_filter` (`core.yaml:308`);
  `includeExpired` (`ClaimsController.java:28`) vs `include_expired` (`core.yaml:106`);
  `pageSize` (`AdminController.java:63`, `ConsentController.java:51`) vs `page_size`
  (`core.yaml:366`). Contract-legal calls are **silently ignored** — filters never
  applied and defaults used.
- `sort=stp_score` is contract-legal (`core.yaml:332`) but rejected with HTTP 400 by
  the `@Pattern` in `AdminController.java:64-66`.

**Fix.** Pick one convention. Simplest: `spring.jackson.property-naming-strategy=SNAKE_CASE`
plus explicit `@RequestParam(name = "...")`. Then add a CI step that regenerates a spec
from the running app and diffs it against `core.yaml`, so drift cannot recur.

### P0-3 · The "verify once, reuse for years" cache never stores anything — silently

`services/core/src/main/java/in/adivritti/core/config/CacheConfig.java:41` builds
`new GenericJackson2JsonRedisSerializer()`. In spring-data-redis 3.3.5 that constructor
creates a plain `ObjectMapper` (only a NullValueSerializer plus default typing;
`findAndRegisterModules` is never called). Serialising a `VerifyResponse` that contains
`ZonedDateTime` (`validUntil`, `attemptedAt`) throws, and the `CacheErrorHandler` in the
same file turns the failed put into a swallowed WARN — so the cache is permanently a
miss while looking healthy.

Two further problems on the same annotation (`VerificationOrchestrator.java:68-72`):
the key ignores `evidenceRef`/`idempotencyKey` (a second, different document inside the
TTL returns the first verdict and never persists a claim), and `@Cacheable` wraps a
state-mutating `@Transactional` method.

**Fix.** Build the serializer from `Jackson2ObjectMapperBuilder` (or persist ISO strings);
key on `usid + claimType + evidenceRef`; move caching to a read path; add a test that a
second verify does not re-probe the adapters.

---

## P1 — security, data integrity, demo-breaking logic

### P1-1 · Four endpoints have no ownership or role check

| Endpoint | Evidence | Consequence |
|---|---|---|
| `POST /v1/verify` | `VerificationController.java:22-25` — no `ScholarAccessGuard` | Any authenticated scholar can write claims/deficiencies for any USID |
| `GET /v1/applications/{id}/timeline` | `ApplicationController.java:18-21`, `ApplicationService.timeline` (`:31-53`) — no ownership lookup | Any application UUID can be enumerated; stage, actor, notes leak |
| `DELETE /v1/consent/{id}` | `ConsentController.java:42-45` — no guard | Any authenticated user can revoke anyone's consent artefact |
| `POST /v1/identity/resolve` | `IdentityController.java:22-25` — no role check | Any authenticated user can mint/link USIDs and probe duplicates |

Contrast with the deliberate guards that do exist: `ScholarsController.java:26`,
`ClaimsController.java:29`, `DisbursementController.java:26`, `EligibilityController.java:27`,
`JagoController.java:38`.

**Fix.** Timeline → resolve the application's USID then `access.check`; consent revoke →
load the artefact, then owner/officer check; verify → `access.check(req.usid())`; identity
resolve → `@PreAuthorize("hasAnyRole('OFFICER','ADMIN')")` (or an internal service
credential). Add MockMvc tests asserting 403 for a non-owner on each.

### P1-2 · Identity resolution links everyone, including non-matches

`services/core/src/main/java/in/adivritti/core/identity/IdentityService.java:60-85`
links every record at its raw score — including scores of 0.0 — and only raises a
boolean flag. `UsidResolver` defines `REVIEW_FLOOR = 0.60` and `REVIEW_CEILING = 0.90`
(`UsidResolver.java:25-26`), but nothing gates persistence on them.

`detectDuplicates` (`IdentityService.java:118-129`) returns early unless more than one
existing USID is found, so the Aadhaar reference-key lookup is dead in the common case:
a fresh `(system, externalId)` pair whose Aadhaar matches an existing scholar **mints a
second USID** rather than surfacing the duplicate (the CAG duplicate-beneficiary
problem the project exists to catch).

**Fix.** Persist only deterministic links or links scoring `>= REVIEW_FLOOR`; queue
0.60–0.90 for officer adjudication *before* linking; always consult
`ScholarRepository.findByAadhaarRefKey` for the anchor. Add unit tests: score 0.0 not
linked; 0.75 queued not linked; fresh external ID with known Aadhaar returns the
existing USID.

### P1-3 · Wallet entries are valueless, so eligibility can never pass a value rule

`VerificationOrchestrator.persistClaim` seals `VALUELESS = ""` (`:126-139`);
`EligibilityService.claimSnapshot` deliberately skips blank values (`:106-109`). The
net effect: "verify once, reuse everywhere" produces a wallet entry that can never
satisfy `family_income_annual_paise`, `class_level`, etc. — no public endpoint can ever
write a claim value, so the demo's `eligible` verdict is unreachable end-to-end.

**Fix.** Persist the adapter-sourced value (e.g. parsed DigiLocker fields) encrypted
into the claim. Keep the "no invented numbers" rule by refusing caller-supplied values
while accepting adapter-derived ones.

### P1-4 · The DPDP consent and audit machinery is never invoked

`git grep` shows **zero callers** for `ConsentService.hasActiveConsent` and
`ConsentService.recordAccess`; `Scholar.guardianUsid` is never read; `granted_by` is
free text (`ConsentDtos.java:20`). The append-only `access_audit` table, its
anti-update triggers (`V1__initial_schema.sql:202-219`), the purpose-bound scope list
and the guardian link for minors are all shipped schema with no wiring. The pitch's
"who looked at my data" story is currently not implemented.

**Fix.** Call `hasActiveConsent` + `recordAccess` on every personal-data read path
(claims, dashboard, disbursements, JAGO tools), and enforce guardian consent for
Pre-Matric minors via `guardian_usid`.

### P1-5 · The AI JAGO proxy routes one tool to the wrong endpoint

`services/ai/app/services/jago_service.py:28-36` maps
`get_my_applications` → `GET /v1/disbursements/{usid}` (payment data, not
applications). Additionally `routers/gap.py:31,40` documents a Core route
`/v1/admin/coverage/gaps` that does not exist (the real path is
`/v1/admin/coverage-gap`).

**Fix.** Correct the route map and add a contract test that the map's paths/methods
exist in `docs/openapi/core.yaml`.

### P1-6 · The officer console's own data can't drive its own logic

- `AdminController.java:100` hard-codes `stpScore = 0.0`, yet the exceptions page gates
  batch auto-approve on `stpScore >= 85` (`apps/officer-web/src/app/dashboard/exceptions/page.tsx:89-99`):
  against the real API the batch action can never run.
- `AdminController.java:70-73`: sorting by `breach_risk` (the documented default) sorts
  by `createdAt`; the comment on `:56-59` claims the opposite.
- `CoverageGapService.java:43-46` returns `totalEnrolled = 0, totalApplicants = 0` while
  the contract and demo script promise school-level enrolment/applicant counts.
- `DashboardService.java:65-67` hard-codes every scheme's `status` to `"in_progress"`
  regardless of `stage`, against a contract enum of
  `eligible|applied|in_verification|approved|disbursed|deficient|not_eligible`.

---

## P2 — correctness, hygiene, documentation drift

**Implementation status (2026-10-01).** P2-1, P2-3, P2-4, P2-5, P2-6 and the CI
contract-drift check are done (suites: core 163 unit tests green, govsim 8/8,
officer-web build + mobile lint clean). P2-2 and P2-7 needed no new work in
this pass: P2-2's dead mobile deps were removed and the offline store honestly
documented as a stub under P1 item 5 (this pass added demo-honest copy on the
claim-refetch and sync actions); P2-7's expo pin and root `app.json` deletion
were also done under P1 item 5.

### P2-1 · Published dev token and `localhost` URLs inside shipped client code

`apps/mobile/src/lib/assistant.ts:164-165` and `apps/officer-web/src/lib/assistant/assistant.ts:161-162`
hard-code `http://localhost:8000/jago/help` plus the published token
`dev-only-ai-service-token`; `apps/officer-web/src/lib/api.ts:48-52` does the same for
`/decisions/stp`. On a phone `localhost` is the phone itself, so the "online-first" path
is dead off-desktop. The AI service and Core have **no CORS configuration** (grep), so
browser calls from the Vercel-hosted console fail on CORS/mixed content anyway; every
online path silently degrades to the local mirror or the deterministic fallback.

**Fix.** Route AI calls through a server-side BFF (Next route handler / Expo API route)
with env-driven base URLs; never ship a service token to a browser bundle.

*Done 2026-10-01.* Officer-web: `src/app/api/jago/help/route.ts` +
`src/app/api/decisions/stp/route.ts` hold `AI_SERVICE_TOKEN` server-side;
`assistant.ts` and `api.ts` call same-origin `/api/…` with no token (build
verified token-free). Mobile: `EXPO_PUBLIC_ASSISTANT_BFF_URL` (build-time, no
token); unset means offline mirror only.

### P2-2 · "Offline-first" is a stub, and six dependencies are dead

`apps/mobile/src/lib/store.ts:27-30` — `deltaSync` returns `pushed: outbox.length`
without pushing anything. `expo-sqlite`, `drizzle-orm`, `react-native-mmkv`,
`react-native-vision-camera`, `expo-camera` and `@tanstack/react-query` are declared in
`apps/mobile/package.json` but never imported anywhere under `apps/mobile/src` (grep).
The module docstring ("SQLite (Drizzle) is the source of truth") describes code that
does not exist.

**Fix.** Implement the outbox + SQLite cache with a real sync call, or delete the stub,
the unused deps and the claim. `apps/mobile/app.json` also lists the `expo-sqlite` and
`expo-camera` config plugins that nothing uses.

### P2-3 · The apps are mock-driven, contradicting `Agent.md`'s non-negotiable

`Agent.md` says "No mock or hardcoded data in `apps/` or `services/core`", yet:
`apps/officer-web/src/lib/api.ts:7-15` imports `__mocks__/data` and
`__mocks__/geo-identity` for every function; `dashboard/coverage-gap/page.tsx:10`
imports `__mocks__` directly, bypassing the api seam; mobile `(tabs)/index.tsx:56`
renders `mockDashboard`; `claim/[id].tsx:50-53` reports "Request sent" with no request;
`profile.tsx:97-98` shows hard-coded Aadhaar/USID values.

**Fix.** Either treat the mock layer as the explicit demo contract (and amend
`Agent.md`), or put the apps on the real client behind an env switch. Right now the
documented rule and the code disagree, which erodes every other "non-negotiable".

*Done 2026-10-01 (option 1).* `Agent.md` now codifies the seam rule (demo data
only in `officer-web/src/lib/api.ts` / `mobile/src/lib/jago.ts`, no
`__mocks__` imports elsewhere, no UI copy claiming effects beyond the screen).
Fixed: coverage helpers moved to `officer-web/src/lib/coverage.ts`;
demo-honest copy on exception approve/outreach toasts, mobile claim refetch and
profile sync.

### P2-4 · `make seed` produces data the application cannot use

`data/synthetic/generate.py:247-254`: `seed.sql` uses `uuid_generate_v4()` (uuid-ossp)
and stores the Aadhaar reference under `"ref"` in `scholar.demographics`, while
`ScholarRepository.findByAadhaarRefKey` reads `$.aadhaarRefKey`; only `scholar` rows
are emitted — no `scholar_system_link`, `claim`, `application`, `disbursement` or
`coverage_candidate` rows. Resolved (non-hashed) reference keys in the generator also
do not use the `AVR1:<keyId>:<hex>` vault format.

**Fix.** Align the JSON key and generator output with the vault contract, use
`gen_random_uuid()`, and seed every read model the dashboards consume.

*Done 2026-10-01.* `seed.sql` emits scholar + links + application +
disbursement + coverage_candidate with `gen_random_uuid()` and `$.aadhaarRefKey`
(output validated); `make seed` now also applies the file to postgres
(`compose exec … psql < data/synthetic/output/seed.sql`). Claims still cannot
be seeded (AES, documented in the generator).

### P2-5 · Documented resilience does not exist; govsim health is subject to chaos

- `resilience4j` and `caffeine` are on the classpath (`services/core/build.gradle:39-40`)
  but referenced nowhere; `docs/specs/govsim-contracts.md` promises "retries with
  exponential backoff + jitter" and "circuit breaker per adapter", while
  `GovsimClient.get` is a single try/catch returning `{available:false}`.
- `services/govsim/src/index.js:63` puts `/health` through the chaos middleware, so the
  health endpoint can 500 (8%) or stall up to ~4 s — the same endpoint used by the
  compose healthcheck (`infra/docker-compose.yml:83-87`) and CI.
- `/digilocker/verify` always returns `verified: true`, so Tier-1 "gov-verified" is a
  rubber stamp; the README's "DigiLocker LIVE" label describes an integration that is
  only a govsim proxy (`DigiLockerAdapter.java:16-20`).

**Fix.** Exempt `/health` from chaos; implement retry/breaker in one place (or correct
the spec); make govsim outcomes deterministic per USID so the demo is reproducible.

*Done 2026-10-01.* `/health` bypasses chaos (was already in the tree, now
covered by an explicit probe test); `GovsimClient` runs programmatic
Resilience4j retry (3 attempts, exponential backoff + jitter, 5xx/IO only) +
circuit breaker with `{available:false}` fallback (`GovsimClientTest` 4/4);
verify outcomes are a stable per-USID hash (demo student always passes,
DigiLocker rejects ~1/8 with `DOCUMENT_MISMATCH`); README no longer claims
DigiLocker LIVE; `govsim-contracts.md` documents the deterministic outcomes.

### P2-6 · Configuration and features that exist on paper only

- `app.security.jwt.issuer` (`application.yml:72`) is never read — no issuer/audience
  validation on JWTs.
- `idempotencyKey` is accepted by `IdentityDtos` and `VerifyDtos` and never used.
- `claim_attestation` (signed JWS issuer, the blueprint's differentiator) and
  `scheme_rule_version` (rules as versioned data, ADR-001) exist as tables with no
  reader/writer; the ADR-mandated startup validation of rule files against
  `packages/rules/schema.json` is not implemented.
- `packages/api-client` has never been generated (only `generate.sh`), so the
  "never hand-edit generated clients" rule currently protects nothing.

*Done 2026-10-01.* Issuer enforced when configured (`SecurityConfigIssuerTest`);
idempotency replay on resolve (`identity_resolution` table) and verify
(`claim.idempotency_key` partial unique index, V2 migration + CI coverage);
`RuleValidator` fail-closed startup validation with `scheme_rule_version`
mirror writer (`RuleBootstrapRunner`) and fallback reader (`EligibilityService`);
`compatibility-matrix.json` moved to `packages/rules/` root (not a scheme rule
file); `make api` proven + CI `api-client` job regenerates and strict-typechecks
the TS client; `claim_attestation` deliberately deferred — see
`docs/adr/ADR-002-accepted-but-unimplemented.md`.

### P2-7 · Mobile build-config risks

`apps/mobile/package-lock.json` resolves `expo-build-properties` to **57.0.22 while
Expo is SDK 52 (`~52.0.0`)** — that pairing does not match the SDK-52 line, so the
`kotlinVersion 1.9.25` fix from commit `e6a656e` may not take effect on EAS; verify
before the next build. The untracked root `app.json` also declares a *different*
Android package (`com.devsunny.adivritti`) than `apps/mobile/app.json`
(`in.gov.mota.adivritti`) — a wrong-config trap if EAS ever resolves from the repo root.

### Verified clean (so the report is not all red)

- In-memory parse of all tracked `.py` (32) and `.json` (29): no syntax errors.
- `node --check` on `services/govsim/src/index.js` and its test: clean.
- `packages/ui-tokens/tokens.json` and `assistant_faq.json` are byte-identical to the
  mobile vendored copies and the AI canonical FAQ (no drift today — but no CI guard).
- Rule JSON only uses ops the engine implements (`eq`, `exists`, `in`, `lte`); valueless
  claims are correctly skipped; `AadhaarVault`, `ClaimValueCipher` and `Money` are
  genuinely well-built and tested.

---

## Proposed remediation plan (ordered, smallest diffs first)

| Step | Work | Files | Why first |
|---|---|---|---|
| 1 | Unblock the stack: rewrite `SecurityConfig` to a single `authorizeHttpRequests` block; add a dev-profile `@SpringBootTest` context-load test | `SecurityConfig.java`, new `SecurityConfigContextTest` | Without this nothing else can be verified by `make dev` |
| 2 | Fix the wire format: snake_case end-to-end (`spring.jackson.property-naming-strategy` + explicit `@RequestParam(name=...)`), accept `sort=stp_score`, add a contract-drift CI check | `application.yml`, `AdminController`, `ClaimsController`, `ConsentController`, `ci.yml` | Every client, test and integration depends on it |
| 3 | Make verification real: register `JavaTimeModule` in the Redis serializer, fix the cache key, move caching to a read path; persist adapter-sourced claim values | `CacheConfig.java`, `VerificationOrchestrator`, `DigiLockerAdapter`, `VerifyDtos` | Restores the product's core promise ("verify once, reuse") |
| 4 | Close the four unguarded endpoints + MockMvc 403 tests for each | `Verification`, `Application`, `Consent`, `Identity` controllers | Data-protection exposure |
| 5 | Fix identity-resolution linking semantics (+ tests for 0.0 / 0.75 / known-Aadhaar cases) | `IdentityService.java`, `UsidResolver`, tests | Prevents false merges and duplicate USIDs |
| 6 | Wire consent + audit into the read paths; enforce guardian consent for minors | `ConsentService`, `DashboardService`, `ClaimsService`, `JagoToolRouter` | Makes the DPDP story true |
| 7 | Reconnect the console: correct the AI route map; compute `stpScore`/`breach_risk` (or drop them from the contract); supply enrolment denominators; derive dashboard `status` from `stage` | `jago_service.py`, `AdminController`, `CoverageGapService`, `DashboardService` | Restores officer workflows the UI already assumes |
| 8 | Housekeeping: govsim `/health` bypass; seed generator aligned with the vault key and all read models; remove or implement the stub offline store and unused deps; reconcile `app.json`/EAS versions; update `Agent.md`/README/ADR to match reality | see P2 sections | Stops the code and the docs from contradicting each other |

## Definition of done for this plan

- `make dev` boots Core + AI + govsim + Postgres + Redis with no exceptions; the CI
  `stack` job is green.
- `make test-core` includes a Spring context test (dev profile) and MockMvc ownership
  tests; `make test-ai` passes on Python 3.13.
- A generated client from `docs/openapi/core.yaml` round-trips a real `VerifyResponse`
  and `DashboardResponse` without a naming shim.
- A second identical `/v1/verify` request is served from Redis (assert by behaviour, not
  by inspecting Redis), and a request with different `evidenceRef` is not.
- Every personal-data read writes an `access_audit` row; revoking a consent blocks the
  reads it authorised.

---

*Working notes: this file is the audit deliverable. Nothing in the repository was changed
as part of producing the audit itself (implementation status below). Sub-agent fan-out for
the audit failed at the platform level (`Unauthorized`), so all findings above were
produced by direct source reading and the specific framework-source checks cited.*

---

## P0 implementation status — done (2026-10-01)

All three P0 blockers are implemented and guarded by tests. `services/core` compiles and
the full suite is green: **19 suites / 126 tests / 0 failures** via
`.\gradlew.bat test -PjavaToolchainVersion=23`. Note for the next person: `JAVA_HOME` on
this machine points at a deleted JDK 17 (`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`),
so the run needs `JAVA_HOME=C:\Program Files\Java\jdk-23` set first.

| Blocker | Change | Test that guards it |
|---|---|---|
| P0-1 the dev stack could not boot | `SecurityConfig.filterChain` is now a single `authorizeHttpRequests` block with one `anyRequest()` call; the dev escape hatch is selected inside the lambda | `SecurityConfigDevChainTest` (context refreshes; anonymous `/v1` permitted), `SecurityConfigHardenedChainTest` (401 without a token, 403 for a scholar on `/v1/admin/**`, 200 for an officer) |
| P0-2 contract ≠ wire format | `spring.jackson.property-naming-strategy: SNAKE_CASE`; explicit `@RequestParam(name = ...)` for `pvtg_filter` / `include_expired` / `page_size`; the `sort` enum in `core.yaml` narrowed to what the service can honour (`breach_risk`, `sla_deadline`, `created_at`) | `WireFormatContractTest` (config check; contract `required` property names for 7 DTOs; inbound `claim_type` / `evidence_ref` / `idempotency_key` binding, and camelCase failing), `ContractQueryParamBindingTest` (the contract's parameter names reach the service and the pageable) |
| P0-3 the verification cache never stored anything | `CacheConfig.jsonSerializer()` registers `JavaTimeModule`, writes ISO-8601, does not re-zone on read, and narrows default typing to an allowlist | `CacheConfigSerializationTest` (records + non-UTC offsets survive), `VerificationCacheBehaviourTest` (a hit skips re-probing; different evidence re-verifies; per-USID scoping; failures are not cached) |

---

## P1 handoff — session state (2026-10-01, after the P0 fixes)

**Verified state.** `services/core` compiles and `.\gradlew.bat test -PjavaToolchainVersion=23`
is green: **19 suites / 126 tests / 0 failures / 0 errors**. Run it with
`JAVA_HOME=C:\Program Files\Java\jdk-23` set first — the machine's `JAVA_HOME` points at a
deleted JDK 17 and `gradlew` refuses to start because of it.

**Uncommitted working tree** (my change set):
`docs/openapi/core.yaml`; `services/core/src/main/resources/application.yml`;
`AdminController`, `ClaimsController`, `ConsentController`, `config/CacheConfig`,
`config/SecurityConfig`, `verification/VerificationOrchestrator`;
new tests `common/WireFormatContractTest`, `common/ContractQueryParamBindingTest`,
`config/{CacheConfigSerializationTest,SecurityConfigDevChainTest,SecurityConfigHardenedChainTest}`,
`verification/VerificationCacheBehaviourTest`.
Not mine (pre-existing): deleted `PROMPT-1-FRONTEND-OPUS5.5.md`, modified blueprint markdown,
untracked `app.json`, `apps/officer-web/screenshots/`, `docs/demo/`, `docs/superpowers/`.

**Repo conventions to keep.** Records for DTOs, no Lombok, AssertJ + JUnit 5 + Mockito;
tests needing Postgres/Redis are `@Tag("integration")` (excluded from `test`); comment style
documents the previous defect in the javadoc ("Previously …, now …"); `core.yaml` is the
contract and the wire format is snake_case; `ScholarAccessGuard` is the IDOR guard
(`access.check(usid)`), `@PreAuthorize("hasAnyRole('OFFICER','ADMIN')")` the role gate.

**P1 queue, in priority order (items 1–2 done, 2026-10-01; suite now 21 suites / 144 tests).**
1. ✅ Closed the four unguarded endpoints: `/v1/verify` + timeline + consent revoke via
   `access.check(...)` (with `usidOf` lookups), `POST /v1/identity/resolve` via the new
   `access.checkOfficer()`; `@PreAuthorize` on `AdminController` replaced by the same guard
   so the dev profile can run the token-free demo. `ScholarAccessGuard` honours
   `allow-insecure-dev` (prod keeps strict gates; bypass logs). Tests:
   `EndpointAuthorisationTest` (8, hardened 403/200 over HTTP),
   `ScholarAccessGuardTest` +4 (incl. dev bypass), binding test updated to officer auth.
2. ✅ Identity linking policy: only `deterministic` or `>= 0.90` rows are persisted
   (review-band gets the `human_adjudication` method the contract documents but the code
   never produced; an all-below-floor batch raises the review flag); duplicates are
   decided over link ∪ Aadhaar USIDs; the anchor reuses the Aadhaar-known USID before
   ever minting. Tests: `IdentityLinkingPolicyTest` (6). Contract description for
   `LinkedSystemRecord` clarified (report, not link table).
3. Wire `hasActiveConsent` / `recordAccess` into the personal-data read paths; enforce
   guardian consent for minors via `guardian_usid`.
4. Officer console reality: real `stp_score`, coverage denominators, dashboard `status`
   derived from `stage`; AI JAGO `get_my_applications` route.
5. ✅ Housekeeping (2026-10-01): govsim `/health` bypasses the chaos middleware
   (registered before `app.use(chaos)`), so the compose healthcheck, CI `stack`
   job, and test-suite readiness poll get a truthful answer; suite readiness
   probe and health test now run WITHOUT the `x-chaos: off` header, and the
   health test pins the bypass (5 consecutive headerless 200s -- any pass
   through chaos would randomly 500/truncate/delay or consume the shared
   20 RPM window). Seed generator emits scholars (vault `$.aadhaarRefKey`
   format via dev-key HMAC), `scholar_system_link` (NSP all + SFMP most),
   `application` (one per NSP row), `disbursement` (PFMS failure sample),
   and `coverage_candidate` (no-NSP students, dev-salt HMAC) -- all
   `gen_random_uuid()` + `ON CONFLICT DO NOTHING`, deterministic uuid5 USIDs,
   verified by regenerating `--demo` twice (identical `seed.sql` hash, zero
   `uuid_generate_v4`/`"ref"` matches). Claim rows deliberately NOT seeded
   (`value_encrypted` is AES-256-GCM; stdlib-only generator has no AES -- use
   POST /v1/verify). Mobile: removed the 4 dead deps
   (`@tanstack/react-query`, `react-native-mmkv`, `react-native-vision-camera`,
   `expo-camera`) + camera plugin/permissions; `store.ts` header now states
   the offline store is NOT wired and `deltaSync` is a no-op returning
   `{ pushed: 0 }` (reporting `outbox.length` as pushed would fake a sync);
   `expo-build-properties` pinned to the SDK-52 line (`0.13.3`, was `57.0.22`),
   `expo-sqlite`/`react-native` aligned to `expo install --check` expectations
   (`Dependencies are up to date`, `tsc --noEmit` clean); deleted the untracked
    root `app.json` that shadowed the Android package (`com.devsunny.adivritti`
    vs `in.gov.mota.adivritti`).
    Tests: govsim suite 6/6 green (incl. the headerless health test).

---

## P1 item 4 remainder + P2 close-out — done (2026-10-02)

**Verified state.** `.\gradlew.bat test -PjavaToolchainVersion=23` (with
`JAVA_HOME=C:\Program Files\Java\jdk-23`) is green: **45 suites / 303 tests /
0 failures / 0 errors**. `node --check services/govsim/src/index.js` clean.
Not run: pytest (no Python on this machine; needs >= 3.13), Docker/compose,
`make dev` boot.

| Remaining item | Change | Test that guards it |
|---|---|---|
| P1-6 coverage denominators (the last open code item) | `CoverageCandidateRepository.aggregateEnrolledBySchool` counts the full enrolled cohort per school; `CoverageGapService` joins gap + enrolled on the school tuple — per-school `enrolledStudents`, `applicants = enrolled - gap`, true totals (fully-reached schools feed totals without appearing in the outreach list). No new table: denominators come from the same privacy-preserving HMAC join | `CoverageGapServiceTest` (4: derived denominators, fully-reached totals, empty → zeros, filter passthrough) |
| Seed emits the enrolled cohort | `generate.py` now seeds `reached` candidate rows for NSP students (same geo/hash shape) and real `pvtg_status` instead of hard-coded `FALSE`, so denominators and PVTG counts are non-zero in a seeded stack. Seed regen needs Python >= 3.13 — not re-run here | covered by the service test above (seed output shape unchanged otherwise) |
| P1-4 consent/audit wiring, P1-5 JAGO route, exceptions bounded scan | confirmed already in tree, not re-done: `ConsentGate.requireConsent` called from `ClaimsService`, `DashboardService`, `DisbursementService`, `JagoToolRouter`; `jago_service.py` routes `get_my_applications` to Core's JAGO tool endpoint; `AdminController` breach-risk scan is a bounded 2,000-row newest-first window (`MAX_RISK_SCAN_ROWS`) with a logged truncation and a true COUNT total | `ConsentGateTest`, `EndpointAuthorisationTest`, `AdminExceptionsTest` (incl. the window-bound test) |

**Definition-of-done mapping.** `make test-core` green (this machine, Oct 2);
contract shape unchanged (`CoverageGapResponse` fields already existed —
only the values are real now, so no client regen needed); Redis-hit and
evidence-scoping behaviour covered by `VerificationCacheBehaviourTest`;
`access_audit` writes on every personal-data read via `ConsentGate`
(allowed + denied). Still requiring a live stack: `make dev` boot,
`make test-ai` on Python 3.13, generated-client round-trip against a running
Core, second-verify-served-from-Redis observed end-to-end.





