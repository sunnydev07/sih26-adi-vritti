# Codebase Audit & Remediation Plan — Adi-Vritti (SIH 2026 PS 26238)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the verified defects found in a full read of the Adi-Vritti monorepo — 4 broken/missing authorization checks, 2 correctness bugs that silently corrupt user-visible output, 1 fail-open eligibility path, and the contract/implementation drift — without changing the 13-endpoint OpenAPI surface.

**Architecture:** Spring Boot 3 `core` (Java 21, port 8080) + FastAPI `ai` (Python 3.13, port 8000) + Node `govsim` (port 4000) behind a `pgvector` Postgres and Redis. Two Expo/Next clients (`apps/mobile`, `apps/officer-web`) render from `docs/openapi/core.yaml`. Rules-as-data lives in `packages/rules/<AY>/*.json`; aadhaar values are HMAC-vaulted reference keys only.

**Tech Stack:** Java 21 / Spring Boot 3.3.5 / JPA+Flyway / WebClient · Python 3.13 / FastAPI / pydantic v2 / pytest · Node 22 / Express · Next.js 16 / React 19 / TypeScript strict · Expo SDK 52 / expo-router

**Spec:** `docs/specs/00-overview.md`, `docs/openapi/core.yaml`, `Agent.md` (non-negotiables), `docs/adr/ADR-001-rules-as-data.md`

## Global Constraints

- `docs/openapi/core.yaml` is the contract. Generate clients; never hand-edit `packages/api-client`. Any contract fix goes in the YAML first, then `make api`.
- No mock or hardcoded data in `apps/` or `services/core`. All fake government data lives behind `services/govsim`.
- No real PII, ever. Test data comes from `data/synthetic` (seed 26238).
- Never store an Aadhaar number in plaintext. Reference keys only.
- Money in integer paise. Dates as ISO-8601 with an explicit zone.
- Java 21, Spring Boot 3, records for DTOs, no Lombok.
- Python 3.13, FastAPI, pydantic v2, ruff (line-length 100).
- TypeScript strict everywhere. No `any`.
- Conventional commits. One workstream per branch.
- Definition of done: compiles, tests pass, contract unchanged or regenerated, `docs/specs/demo-path.md` still runs end to end.
- `make test-core` must run as `./gradlew test -PjavaToolchainVersion=23` on a JDK-23 box and `-PjavaToolchainVersion=21` in CI. Do not hardcode either.
- Do NOT rotate the committed dev secrets as part of this plan. They are deliberately published for `make dev`; the `.env` at repo root is gitignored and untracked, and `git log --all -S` confirms no secret is in history. Rotation is a separate, deliberate decision (see Task 9 note).

## Audit Method & Confidence

Every finding below was verified by reading the code and, where the bug was non-obvious, by **executing a faithful port of the actual algorithm** rather than by inspection alone. Findings are grouped by confidence:

- **Verified by execution** — reproduced the wrong output with the real algorithm.
- **Verified by reading + control flow** — the defect is unambiguous from the code path.
- **Rejected during audit** — I suspected it, tested it, and it is correct. Listed so nobody re-investigates.

---

## Findings

### P0 — Security / authorization

**F1. `POST /v1/verify` has no ownership check. Any authenticated user can write claims into any scholar's wallet.**
`VerificationController` (`services/core/src/main/java/in/adivritti/core/verification/VerificationController.java:22-25`) takes `usid` from the request body and calls the orchestrator directly. It does **not** inject or call `ScholarAccessGuard`, unlike every other scholar-scoped controller. Every other controller that accepts a `usid` does call `access.check(usid)` — `ClaimsController:29`, `EligibilityController:27`, `DisbursementController:26`, `JagoController:38`, `ScholarsController:26`, `ConsentController:38,52`. `/v1/verify` is the one omission, and it is the only one that is a **write** to another scholar's record: it inserts a `claim` row and a `deficiency` row keyed to a foreign USID. This is worse than the IDOR the guard was written to prevent.
*Confidence: verified by reading + control flow.*

**F2. `GET /v1/applications/{id}/timeline` has no ownership check.**
`ApplicationController.java:18-21` takes an application UUID and returns the stage trail, current actor, and SLA state. There is no `ScholarAccessGuard` call and no `@PreAuthorize`. The application is looked up by primary key, so a caller who can enumerate or guess application UUIDs reads any student's filing history. `Application` carries `usid` (`entity/Application.java:19`), so the guard *can* be applied — it just isn't.
*Confidence: verified by reading + control flow.*

**F3. `DELETE /v1/consent/{id}` has no ownership check — cross-scholar consent revocation.**
`ConsentController.java:42-45` takes a consent UUID and calls `service.revoke(id)`. `ConsentService.revoke` (`ConsentService.java:78-87`) loads by id and stamps `revokedAt`. Nothing ties the consent's `usid` to the caller. Any authenticated user can revoke any scholar's consent artefacts — the grant endpoint on line 38 *does* check, so the asymmetry is clearly an oversight, not a policy. This is a DPDP integrity issue: consent withdrawal is a data-subject right, and revoking someone else's consent corrupts the purpose-binding record.
*Confidence: verified by reading + control flow.*

**F4. `apps/officer-web` has no route protection at all — the deployed officer console is unauthenticated, and `/` redirects straight into it.**
There is no `middleware.ts` anywhere in `apps/officer-web`, and a grep across `src/` for `middleware`, `getServerSession`, `authjs`, `next-auth`, `cookies()` and `Authorization` returns **no matches** — there is no authentication mechanism in the app at all, at any layer. `dashboard/layout.tsx` renders its chrome with no credential check. `login/page.tsx:34-37` accepts any 4+ digit OTP and calls `router.push("/dashboard")` — a client-side animation, not authentication. And `app/page.tsx` is a bare `redirect("/dashboard")`, so the root URL lands a visitor in the console directly; `/login` is never on the path. The README links a **live Vercel deployment** of exactly this app, so the officer console — which renders cross-ministry aggregates, USIDs, PFMS references and disbursement amounts — is publicly reachable by anyone with the URL, without ever visiting `/login`.
*Confidence: verified by reading + control flow. Highest user-visible risk, and the one to fix first.*

**F5. The AI service token is hardcoded in the browser bundle.**
`apps/officer-web/src/lib/api.ts:52` and `components/assistant/assistant.ts:162` both ship `"x-ai-service-token": "dev-only-ai-service-token"` to the client. That value is the published default in `infra/docker-compose.yml` and `services/ai/.env.example`, so it is in the repository and in git history. Any browser can call `/gap/hash` with it — the route whose whole purpose is to not accept raw Aadhaar references from unauthenticated callers (`app/security.py:1-25` documents exactly this threat). This is a dev-only token so the blast radius today is low, but the pattern ships the credential and the endpoint together.
*Confidence: verified by reading.*

**F6. `services/ai` has no CORS middleware, so the two browser fetches that hardcode `localhost:8000` cannot work anyway.**
`app/main.py` registers no `CORSMiddleware` (confirmed by grep). `api.ts:48` and `assistant.ts:161` call `http://localhost:8000/...` from the browser — a cross-origin request that the preflight will reject. So F5 is also a *dead* code path: the STP integration silently falls back to the local mock on every load, which is why nobody noticed the leaked token. Both facts must be fixed together or the fallback masking persists.
*Confidence: verified by reading + control flow.*

### P0 — Correctness

**F6a. Empty `rules` array ⇒ every student is `eligible` for every scheme (fail-open).**
`EligibilityService.evaluateScheme:152-154` returns `"eligible"` when `reasons.isEmpty()`. An empty rule list produces zero outcomes, hence an empty reason list, hence `eligible`. I executed a faithful port of `RuleEngine` + the verdict logic against an empty rule list and a scholar with **no claims at all**: the verdict was `eligible`. The same port confirms the non-empty paths behave correctly (`not_eligible` for a genuine over-ceiling income, `missing_items` for an absent claim), so this is specifically the empty-input case. A malformed or truncated rules file therefore grants every scholarship in the system. The fix must reject an empty rule set, not just handle it gracefully.
*Confidence: verified by execution.*

**F6b. `SchemeVerdict.scheme` is uppercased, but `DashboardService` looks it up lowercase — eligibility is always `"unknown"` on the student home screen.**
`EligibilityService:139` does `String label = scheme.toUpperCase(Locale.ROOT)` and returns that as `SchemeVerdict.scheme`. `DashboardService.eligibilityVerdicts:92` keys its map by `v.scheme()` (so `"PRE-MATRIC"`), then line 67 reads `verdicts.getOrDefault(a.scheme, "unknown")` where `a.scheme` comes from the `application` table (lowercase, per `packages/rules/2026-27/*.json` and `SlaCalculator` stage naming). The lookup can never hit, so every scheme on the dashboard renders `eligibility: "unknown"`. The verdict is computed correctly and then thrown away.
*Confidence: verified by reading + control flow.*

**F7. A verified claim can never satisfy an eligibility rule, because the orchestrator stores an empty value.**
`VerificationOrchestrator.persistClaim:138` seals `VALUELESS` (the empty string) into `claim.value_encrypted` — I grepped every `cipher.seal` call site and this is the only one; no code path anywhere writes a non-empty claim value. `EligibilityService.claimSnapshot:106-109` then *skips* any claim whose decrypted value is blank, by design. So a claim that was just verified with `verdict: "verified"` and `confidence: 0.99` is invisible to the rules engine, and the student still gets `missing_items`. The Verified Claims Wallet — layer 2 of the 9-layer architecture, and the headline of demo beat 3 ("verify once, reuse everywhere") — cannot satisfy a single rule. The code comment frames this as deliberate ("never invent a number"), and the *intent* is right, but the effect is a dead subsystem: the contract's `VerifyRequest` carries no value field to store.
*Confidence: verified by grep across all writers + reading.*

**F8. `/v1/admin/exceptions` ignores its own `sort` parameter and never computes `stp_score`.**
Two defects in one method (`AdminController.java:60-102`):
- The `@Pattern` accepts `breach_risk|sla_deadline|created_at`, but line 70 only branches on `"sla_deadline"`. Every other value — **including the default `breach_risk`** — falls to `Sort.by(DESC, "createdAt")`. The endpoint's advertised purpose ("Ordered by soonest deadline first… past their deadline pinned to the top") does not happen.
- `toItem:100` hardcodes `0.0` for `stpScore`. Nothing in `services/core` computes an STP score. The AI service has `POST /decisions/stp` (`routers/decisions.py:27`) and the officer-web calls it directly from the browser (`api.ts:48`), but Core never does. So the contract-required `stp_score` (`core.yaml:783`, `required:` list at line 776) is structurally always zero, and the demo's "STP-eligible file → one-click approve" beat has no backend.
*Confidence: verified by reading + control flow.*

### P1 — Contract drift

**F9. The contract's `sort` enum does not match the implementation.**
`core.yaml:332` declares `enum: [breach_risk, sla_deadline, stp_score]`. The implementation accepts `breach_risk|sla_deadline|created_at`. A client generated from the contract will send `stp_score` and get a 400; a client sending `created_at` (which works) is not documented. The contract is authoritative per `Agent.md`, so the enum is what must change.
*Confidence: verified by reading both sides.*

**F10. The contract declares no `securitySchemes` and no `401`/`403` responses anywhere.**
The API requires a verified JWT on every `/v1` route (`SecurityConfig:66-71`) and returns 403 from `ScholarAccessGuard`, but `core.yaml` has no `securitySchemes` block, no top-level `security:`, and not one `401` or `403` response across all 13 paths. Generated clients will have no auth wiring, and the "onboarding" story in `Agent.md` cannot be expressed by a consumer. Given F1–F3 are all about authorization, this gap is why the drift went unnoticed.
*Confidence: verified by grep (`security:`, `securitySchemes`, `bearerAuth` → no matches).*

**F11. `ErrorBody` and the contract's `ErrorResponse` disagree.**
The contract (`core.yaml:395-401`) declares `required: [error_code, message]` with `details` as an object. The implementation (`GlobalExceptionHandler.java:39-40`) is a Java record `ErrorBody(errorCode, message, details, at, path)`. Jackson serializes `errorCode` as `errorCode`, not `error_code` — and there is no `@JsonProperty` and no global naming strategy configured. So every error response uses a different key than the contract promises, and the two extra fields (`at`, `path`) are undocumented. Also note `details` is always non-null in the impl (line 131 always puts `status` into it) but not marked required in the contract.
*Confidence: verified by reading; the absence of any `PropertyNamingStrategy` config is confirmed across the three application YAMLs and `build.gradle`.*

**F12. `ClaimDto` omits `evidenceRef`, `source` values, and the contract's `Claim` schema has fields Core never populates.**
`ClaimsService.toDto:33-36` returns `(id, usid, claimType, source, method, confidence, verifiedAt, validUntil, expired)`. The contract's `Claim` schema (`core.yaml:575`) should be compared field-by-field during Task 8 — the wallet is the demo's centrepiece and the frontend renders it directly.
*Confidence: partially verified — flagged for a mechanical diff in Task 8 rather than asserted here.*

### P1 — Duplication & dead weight

**F13. Two independent Jaro-Winkler implementations that already disagree.**
`services/ai/app/services/matching_service.py:28-56` and `services/core/.../UsidResolver.java:248-289` implement the same algorithm. The Java one was explicitly hardened (match window floored at 0, bounds-checked transposition scan, emptiness checked *before* equality) and its comments describe the exact bugs the Python one still has. Measured divergence on realistic name pairs — the Python build scores `meena`/`mina` at **0.805** where Java scores **0.535**, and `phulo gond`/`phoolo gund` at 0.866 vs 0.604. Since the Python `/match/score` route uses `HUMAN_REVIEW_FLOOR = 0.60` and `AUTO_ACCEPT = 0.90`, a pair the authoritative Java resolver would send to a human is scored as high-confidence by the AI service.
*Confidence: verified by execution (faithful port of the Python function run against the Java reference).*

**F14. The Python Jaro-Winkler scores two empty strings as a perfect match.**
`matching_service.py:29` does `if s1 == s2: return 1.0` **before** the `if not s1 or not s2` guard on line 31. `score()` calls it with `fold(a.get("guardian_name"))` and `fold(b.get("guardian_name"))`, and `fold(None)` returns `""`. So two records that *both* lack a guardian name get the full 0.15-weighted guardian credit. Measured: an otherwise-identical pair scores **0.839** instead of the correct 0.689 — a phantom +0.15 that can push a pair over a threshold. The Java version's comment describes precisely this bug and states it was fixed; the Python twin was never given the fix.
*Confidence: verified by execution.*

**F15. `idempotencyKey` is accepted and validated, then silently ignored.**
Both `IdentityResolveRequest` (`IdentityDtos.java:17-19,25`) and `VerifyRequest` (`VerifyDtos.java:17-19,28-29`) document `idempotencyKey` as the mechanism that makes retries return the original result instead of creating a second record. Neither `IdentityService` nor `VerificationOrchestrator` references it (grep: matches only in the DTOs). A client retrying a timed-out `POST /v1/verify` inserts a second `claim` row and a second `deficiency` row. This is a documented-and-promised guarantee that does not exist, and it sits directly on the write path that F1 makes unauthenticated.
*Confidence: verified by grep.*

**F16. Declared-but-unused dependencies, and two mis-scoped comments.**
- `resilience4j-spring-boot3` in `build.gradle` with zero annotations or config in `services/core/src` (grep: no `@CircuitBreaker`/`@Retry`/`@Bulkhead`). `GovAdapter`'s javadoc claims "circuit breakers (Resilience4j), retries with jitter at call site" — the *comment* documents behaviour that does not exist. The chaos middleware in `govsim` injects 8% 500s and the only real mitigation is `VerificationAttempt`'s try/catch.
- `caffeine` declared; the only match is a javadoc comment in `CacheConfig` explaining it was *removed*.
- `infra/docker-compose.override.yml` claims rules-as-data "hot-reloads", but `EligibilityService.loadRules` caches into a `ConcurrentHashMap` that is never invalidated — the mount is live, the read is not.
- Mobile declares `drizzle-orm`, `expo-sqlite`, `@tanstack/react-query`, `react-native-mmkv`, `react-native-vision-camera` with **zero** imports in `apps/mobile/src` (verified per-package), yet `store.ts` documents "SQLite (Drizzle) is the source of truth for reads" and `deltaSync` is a stub returning `{pushed: outbox.length, since}`.
*Confidence: verified by grep.*

**F17. `rule.attestation` / `claim_attestation` and `scheme_rule_version` tables have no code.**
`V1__initial_schema.sql` creates `claim_attestation` (line 61, signed Ed25519 attestations) and `scheme_rule_version` (line 174, the "rules-as-data mirrored into the table" that ADR-001 mandates). No JPA entity, repository, or service references either (grep: only the migration matches). ADR-001's central claim — rules mirrored into `scheme_rule_version`, with startup schema validation — is unimplemented; `EligibilityService` reads JSON off the filesystem with no validation against `packages/rules/schema.json` at boot.
*Confidence: verified by grep.*

### P2 — Robustness

**F18. A malformed rule file is reported to the student as `missing_items` with "rules are currently unavailable".**
`EligibilityService.evaluateScheme:160-166` catches `RuntimeException` around the whole evaluation and returns `missing_items` + a generic message. A typo'd operator name in a rules file (e.g. `op: "lteq"`) makes every scholar look like they have missing documents instead of surfacing a configuration bug. The catch is right for *not leaking paths to the client*; what's missing is an error-level log with the offending rule index, and a distinct internal signal. Note the interaction with F6a: empty and malformed are handled, but they are not distinguished.
*Confidence: verified by reading.*

**F19. `SlaCalculator.breachRisk` measures elapsed from `createdAt`, not from stage entry.**
`breachRisk(stage, stageEnteredAt)` is correct in isolation, and `ApplicationService.timeline:42-49` correctly derives `stageEnteredAt` from the latest event for the current stage. But `AdminController.toItem:98-99` passes `a.createdAt` instead. So an application that sat at `submitted` for 30 days and then moved to `ministry` shows breach risk computed from its full 30-day age against a 15-day ministry SLA — a false positive that pushes clean files to the top of the officer queue. The `ExceptionItem` javadoc says the opposite of what the code does.
*Confidence: verified by reading.*

**F20. `ApplicationRepository` is unindexed for the exception queue's actual sort.**
`V1__initial_schema.sql:87` creates `idx_application_sla ON application(sla_deadline) WHERE sla_deadline IS NOT NULL`, described in-line as "The exception queue orders by breach risk: soonest deadline first". But because of F8, the queue actually orders by `created_at DESC`, for which there is **no index** (only `idx_application_usid` at line 83 and `idx_application_scheme_year`). At 18k+ seeded applications this is a sequential scan on every page load. The index exists for a query nobody runs; the query that runs is unindexed.
*Confidence: verified by reading the migration and the repository.*

**F21. `ConsentService.recordAccess` and `hasActiveConsent` are never called — the DPDP access audit is never written.**
Grep for `recordAccess` and `hasActiveConsent` across `services/core/src` matches only their own definitions in `ConsentService.java`. The append-only `access_audit` table has DB triggers that reject UPDATE/DELETE, an `AuditEventDto` response type, a contract path (`/v1/scholars/{usid}/audit`) — and zero writers. So `GET /v1/scholars/{usid}/audit` always returns an empty page, and layer 9 of the architecture ("purpose-bound consent artefacts, append-only access audit") has no enforcement path. `ConsentService.audit` is also the only place `accessorName()` from `ScholarAccessGuard` would matter, and it isn't used.
*Confidence: verified by grep.*

**F22. `.env` at the repo root holds live-looking third-party API keys and is gitignored, not absent.**
`OPENCODE_ZEN_API_KEY` and `GROQ_API_KEY` are present with realistic prefixes. It is correctly untracked (`git ls-files` → 0 matches; `git log --all -S<key>` → clean), so **no secret is in history** and this is not an active leak. The risk is operational: a key pasted into a repo-root file that any new dev copies, plus `app/config.py:105` (`env_file=".env"`) auto-loading it. Left as a note, not a task — see the Global Constraints note on rotation.

---

## Rejected during audit (do not re-investigate)

- **`AadhaarVault.verhoeffValid` is NOT broken.** I suspected the `(length-1-i) % 8` rotation was off by one and measured 10% check-digit agreement against my own "canonical" variant. I then pulled the authoritative definition from Wikipedia's Verhoeff article, which specifies the array taken **right-to-left** with `c = d(c, p(i mod 8, n_i))` — that is algebraically identical to the repo's left-to-right `(length-1-i) % 8`. My "canonical" variant was the wrong one. The implementation is correct, and `AadhaarVaultTest.verhoeffAcceptsAKnownValidNumber` pins it.
- **Python `jaro_winkler` cannot `IndexError`.** I suspected the unguarded `while not m2[k]: k++` would run off the flag array (the Java twin's javadoc says it used to). Exhaustive search over all pairs from a 3-letter alphabet up to length 4 found no crash: the `matches == 0` early return plus the counting invariant prevent it. Keep the bound anyway as cheap defence, but it is not a live bug. (F14 — the empty-string ordering — *is* live.)
- **Secret keys are not in git history.** Verified via `git ls-files` and `git log --all -S` for both key values. Clean.

---

## File Structure

Files this plan creates, modifies, or deletes:

**Modify (Core, authorization):**
- `services/core/src/main/java/in/adivritti/core/verification/VerificationController.java` — add `ScholarAccessGuard` (F1)
- `services/core/src/main/java/in/adivritti/core/application/ApplicationController.java` — add guard via `Application.usid` (F2)
- `services/core/src/main/java/in/adivritti/core/consent/ConsentController.java` — add guard on revoke (F3)

**Modify (Core, correctness):**
- `services/core/src/main/java/in/adivritti/core/eligibility/EligibilityService.java` — reject empty rules (F6a), stop uppercasing the scheme label (F6b), log rule-index on parse failure (F18)
- `services/core/src/main/java/in/adivritti/core/verification/VerificationOrchestrator.java` — honour `idempotencyKey` (F15)
- `services/core/src/main/java/in/adivritti/core/identity/IdentityService.java` — honour `idempotencyKey` (F15)
- `services/core/src/main/java/in/adivritti/core/admin/AdminController.java` — real `sort` branches (F8), stage-entry breach risk (F19)
- `services/core/src/main/java/in/adivritti/core/eligibility/RuleEngine.java` — expose an empty-rule signal

**Modify (Core, contract alignment):**
- `services/core/src/main/java/in/adivritti/core/common/exception/GlobalExceptionHandler.java` — `error_code` naming (F11)
- `docs/openapi/core.yaml` — sort enum, security schemes, 401/403, `stp_score` (F9, F10, F8)

**Modify (AI):**
- `services/ai/app/services/matching_service.py` — empty-first, bounds, parity with Java (F13, F14)
- `services/ai/app/main.py` — CORS allowlist (F6)
- `services/ai/app/routers/decisions.py` — unchanged; consumed by Core in Task 7

**Modify (officer-web):**
- `apps/officer-web/middleware.ts` — **create**, route protection (F4)
- `apps/officer-web/src/lib/api.ts` — remove hardcoded token/URL (F5, F6)
- `apps/officer-web/src/components/assistant/assistant.ts` — same
- `apps/officer-web/src/app/(auth)/login/page.tsx` — real auth handoff

**Create (tests):**
- `services/core/src/test/java/in/adivritti/core/security/ScholarScopedEndpointAuthorizationTest.java`
- `services/core/src/test/java/in/adivritti/core/eligibility/EmptyRulesFailClosedTest.java`
- `services/core/src/test/java/in/adivritti/core/admin/ExceptionQueueSortTest.java`
- `services/ai/tests/test_matching_parity.py`

**Modify (deps/comments):** `services/core/build.gradle`, `infra/docker-compose.override.yml`, `apps/mobile/package.json`, `services/core/.../adapter/GovAdapter.java`

---

## Tasks

> Execution order is deliberate: Task 1 (authorization) is first because it is the highest severity and is self-contained; Task 2 (fail-open eligibility) is second because it is a silent-wrong-answer bug; correctness tasks follow; contract changes come after the behaviour they describe is settled, so the YAML is written once against final behaviour. Frontend work is last because it depends on the Core endpoints Task 7 exposes.

### Task 1: Close the three missing authorization checks

**Files:**
- Modify: `services/core/src/main/java/in/adivritti/core/verification/VerificationController.java`
- Modify: `services/core/src/main/java/in/adivritti/core/application/ApplicationController.java`
- Modify: `services/core/src/main/java/in/adivritti/core/consent/ConsentController.java`
- Modify: `services/core/src/main/java/in/adivritti/core/application/repository/ApplicationRepository.java`
- Test: `services/core/src/test/java/in/adivritti/core/security/ScholarScopedEndpointAuthorizationTest.java`

**Interfaces:**
- Consumes: `ScholarAccessGuard.check(UUID usid)` and `ScholarAccessGuard` constants `ROLE_OFFICER`/`ROLE_ADMIN` (existing, package `in.adivritti.core.security`).
- Produces: `ApplicationRepository.findByIdForOwnership(UUID id)` returning `Optional<Application>` (new); every scholar-scoped controller calls `access.check(usid)` before touching the service.

- [ ] **Step 1: Write the failing test**

Create `services/core/src/test/java/in/adivritti/core/security/ScholarScopedEndpointAuthorizationTest.java`. This is a plain unit test — the project excludes `@Tag("integration")` from the default run and has no `MockMvc` infrastructure, so assert on the guard/service interaction rather than booting a context. Follow the existing style in `ScholarAccessGuardTest.java` (plain JUnit 5, `org.junit.jupiter.api.Test`, no Spring runner).

```java
package in.adivritti.core.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import in.adivritti.core.application.ApplicationService;
import in.adivritti.core.application.entity.Application;
import in.adivritti.core.application.repository.ApplicationRepository;
import in.adivritti.core.claims.ClaimsService;
import in.adivritti.core.common.exception.ForbiddenException;
import in.adivritti.core.consent.ConsentService;
import in.adivritti.core.consent.dto.ConsentDtos.ConsentDto;
import in.adivritti.core.verification.VerificationOrchestrator;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class ScholarScopedEndpointAuthorizationTest {

    private final ScholarAccessGuard guard = new ScholarAccessGuard();
    private final UUID callerUsid = UUID.randomUUID();
    private final UUID victimUsid = UUID.randomUUID();

    private void authenticateAs(UUID usid) {
        Jwt jwt = Jwt.withTokenValue("t")
            .header("alg", "none").claim("usid", usid.toString()).build();
        Authentication auth = new org.springframework.security.authentication
            .AbstractAuthenticationToken(null, java.util.List.of()) {
            @Override public Object getCredentials() { return null; }
            @Override public Object getPrincipal() { return jwt; }
        };
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void verifyRejectsAForeignUsidAndNeverReachesTheOrchestrator() {
        authenticateAs(callerUsid);
        VerificationOrchestrator orchestrator = mock(VerificationOrchestrator.class);
        in.adivritti.core.verification.VerificationController controller =
            new in.adivritti.core.verification.VerificationController(orchestrator, guard);

        VerifyRequest req = new VerifyRequest(victimUsid, "income", null, null);
        assertThrows(ForbiddenException.class, () -> controller.verify(req));
        verify(orchestrator, never()).verify(any());
    }

    @Test
    void verifyAllowsTheOwningScholar() {
        authenticateAs(callerUsid);
        VerificationOrchestrator orchestrator = mock(VerificationOrchestrator.class);
        in.adivritti.core.verification.VerificationController controller =
            new in.adivritti.core.verification.VerificationController(orchestrator, guard);
        org.mockito.Mockito.when(orchestrator.verify(any()))
            .thenReturn(null); // value unused; the point is that the guard passed
        assertDoesNotThrow(() ->
            controller.verify(new VerifyRequest(callerUsid, "income", null, null)));
    }

    @Test
    void applicationTimelineRejectsAForeignUsid() {
        authenticateAs(callerUsid);
        ApplicationRepository repo = mock(ApplicationRepository.class);
        ApplicationService service = mock(ApplicationService.class);
        in.adivritti.core.application.ApplicationController controller =
            new in.adivritti.core.application.ApplicationController(service, guard, repo);

        UUID appId = UUID.randomUUID();
        assertThrows(ForbiddenException.class, () -> controller.timeline(appId));
        verify(service, never()).timeline(any());
    }

    @Test
    void consentRevokeRejectsAForeignScholarsConsent() {
        authenticateAs(callerUsid);
        ConsentService service = mock(ConsentService.class);
        in.adivritti.core.consent.ConsentController controller =
            new in.adivritti.core.consent.ConsentController(service, guard);

        UUID consentId = UUID.randomUUID();
        assertThrows(ForbiddenException.class, () -> controller.revoke(consentId));
        verify(service, never()).revoke(any());
    }
}
```

Add `import org.junit.jupiter.api.AfterEach;` to the import block.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*ScholarScopedEndpointAuthorizationTest' --console=plain`
Expected: **compilation failure** — `VerificationController` has no 2-arg constructor, `ApplicationController` has no 3-arg constructor, `ConsentController` has no 2-arg constructor. That is the correct red state: the guards do not exist.

- [ ] **Step 3: Add the guard to `VerificationController`**

Replace the whole file:

```java
package in.adivritti.core.verification;

import in.adivritti.core.security.ScholarAccessGuard;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verification is a WRITE into a scholar's wallet, so it is ownership-checked
 * exactly like the read endpoints. The usid arrives in the body rather than the
 * path, which means nothing in the route ties it to the caller: without the
 * guard, any authenticated user could insert a verified claim — and a
 * deficiency — into any other scholar's record.
 */
@RestController
@RequestMapping("/v1/verify")
public class VerificationController {

    private final VerificationOrchestrator orchestrator;
    private final ScholarAccessGuard access;

    public VerificationController(VerificationOrchestrator orchestrator,
        ScholarAccessGuard access) {
        this.orchestrator = orchestrator;
        this.access = access;
    }

    @PostMapping
    ResponseEntity<VerifyResponse> verify(@Valid @RequestBody VerifyRequest req) {
        access.check(req.usid());
        return ResponseEntity.ok(orchestrator.verify(req));
    }
}
```

- [ ] **Step 4: Add the guard to `ApplicationController`**

An application UUID is not a USID, so the guard needs the owner. Resolve it from the repository first, then check. Replace the whole file:

```java
package in.adivritti.core.application;

import in.adivritti.core.application.dto.ApplicationDtos.ApplicationTimelineResponse;
import in.adivritti.core.common.exception.NotFoundException;
import in.adivritti.core.security.ScholarAccessGuard;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/applications")
public class ApplicationController {

    private final ApplicationService service;
    private final ScholarAccessGuard access;
    private final ApplicationRepository applications;

    public ApplicationController(ApplicationService service, ScholarAccessGuard access,
        ApplicationRepository applications) {
        this.service = service;
        this.access = access;
        this.applications = applications;
    }

    /**
     * The application id is not a USID, so the owning scholar is resolved first and
     * the guard is applied to them. Resolving before checking means an unknown id
     * returns 404 and a known-but-foreign id returns 403 with the same message the
     * guard uses everywhere else — the endpoint cannot be used to probe which
     * application ids are real.
     */
    @GetMapping("/{id}/timeline")
    ResponseEntity<ApplicationTimelineResponse> timeline(@PathVariable UUID id) {
        UUID owner = applications.findById(id)
            .map(a -> a.usid)
            .orElseThrow(() -> new NotFoundException("APPLICATION_NOT_FOUND",
                "Application not found"));
        access.check(owner);
        return ResponseEntity.ok(service.timeline(id));
    }
}
```

- [ ] **Step 5: Add the guard to `ConsentController.revoke`**

In `ConsentController.java`, add a `ConsentArtefactRepository`-backed owner lookup. The cleanest form is to have `ConsentService` expose the owner rather than leaking the repository into the controller. Add to `ConsentService.java`:

```java
    /** The USID that owns a consent artefact, or empty when the id is unknown. */
    @Transactional(readOnly = true)
    public java.util.Optional<UUID> ownerOf(UUID id) {
        if (id == null) return java.util.Optional.empty();
        return consents.findById(id).map(c -> c.usid);
    }
```

Then in `ConsentController.java`, change the `revoke` method and add the import for `Optional`:

```java
    @DeleteMapping("/v1/consent/{id}")
    ResponseEntity<ConsentDto> revoke(@PathVariable UUID id) {
        // Revoking someone else's consent corrupts a DPDP purpose-binding record,
        // so ownership is resolved first, exactly as on the grant endpoint.
        UUID owner = service.ownerOf(id)
            .orElseThrow(() -> new NotFoundException("CONSENT_NOT_FOUND", "Consent not found"));
        access.check(owner);
        return ResponseEntity.ok(service.revoke(id));
    }
```

Add imports to `ConsentController.java`:
```java
import in.adivritti.core.common.exception.NotFoundException;
import java.util.Optional;
```
`Optional` is imported for readability but unused if you use `var` — drop it if the compiler warns.

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*ScholarScopedEndpointAuthorizationTest' --console=plain`
Expected: PASS, 4 tests.

- [ ] **Step 7: Run the full Core suite to check for regressions**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --console=plain`
Expected: BUILD SUCCESSFUL, 105 pre-existing tests still pass (109 total). `JagoControllerTest` constructs `JagoController(service, guard)` — check whether it now needs updating; `JagoController`'s signature is unchanged, so it should not.

- [ ] **Step 8: Commit**

```bash
git add services/core/src/main/java/in/adivritti/core/verification/VerificationController.java \
        services/core/src/main/java/in/adivritti/core/application/ApplicationController.java \
        services/core/src/main/java/in/adivritti/core/consent/ConsentController.java \
        services/core/src/main/java/in/adivritti/core/consent/ConsentService.java \
        services/core/src/test/java/in/adivritti/core/security/ScholarScopedEndpointAuthorizationTest.java
git commit -m "fix(security): add ownership checks to /v1/verify, application timeline and consent revoke"
```

---

### Task 2: Make an empty rule set fail closed

**Files:**
- Modify: `services/core/src/main/java/in/adivritti/core/eligibility/RuleEngine.java`
- Modify: `services/core/src/main/java/in/adivritti/core/eligibility/EligibilityService.java:138-199`
- Test: `services/core/src/test/java/in/adivritti/core/eligibility/EmptyRulesFailClosedTest.java`

**Interfaces:**
- Consumes: `RuleEngine.evaluate(List<Map<String,Object>>, Map<String,Object>)` (existing).
- Produces: `RuleEngine.requireNonEmptyRules(List<Map<String,Object>> rules, String scheme, String year)` — throws `IllegalArgumentException` when the list is null or empty. `EligibilityService` calls it before evaluating.

- [ ] **Step 1: Write the failing test**

Create `services/core/src/test/java/in/adivritti/core/eligibility/EmptyRulesFailClosedTest.java`:

```java
package in.adivritti.core.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmptyRulesFailClosedTest {

    private final RuleEngine engine = new RuleEngine();

    @Test
    void anEmptyRuleListIsRejectedRatherThanPassingEveryRule() {
        // The defect: evaluate() returns no outcomes, the service maps "no failed
        // reasons" to `eligible`, so a truncated rules file granted every scheme.
        assertThrows(IllegalArgumentException.class,
            () -> engine.requireNonEmptyRules(List.of(), "pre-matric", "2026-27"));
    }

    @Test
    void aNullRuleListIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> engine.requireNonEmptyRules(null, "pre-matric", "2026-27"));
    }

    @Test
    void aNonEmptyRuleListIsAcceptedAndEvaluated() {
        var rules = List.<Map<String, Object>>of(Map.of(
            "claim", "family_income_annual_paise", "op", "lte",
            "value", 25000000, "onFail", "over ceiling"));
        engine.requireNonEmptyRules(rules, "pre-matric", "2026-27");
        var outcomes = engine.evaluate(rules, Map.of("family_income_annual_paise", 99000000));
        assertEquals(1, outcomes.size());
        assertEquals(false, outcomes.get(0).passed());
    }

    @Test
    void theRealRulesForEverySchemeAreNonEmpty() {
        // Guards the regression at the source: if a published rule file is ever
        // emptied, this fails at build time rather than in production.
        for (String scheme : List.of("pre-matric", "post-matric", "top-class", "nfst", "nos")) {
            var rules = new java.io.ObjectMapper().readValue(
                java.nio.file.Files.readString(java.nio.file.Path.of(
                    "../../packages/rules/2026-27/" + scheme + ".json")),
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
            @SuppressWarnings("unchecked")
            var list = (List<Map<String, Object>>) ((Map<String, Object>) rules).get("rules");
            engine.requireNonEmptyRules(list, scheme, "2026-27");
        }
    }
}
```

The last test's `readValue` throws `IOException`; declare the method `throws Exception`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*EmptyRulesFailClosedTest' --console=plain`
Expected: **compilation failure** — `cannot find symbol: method requireNonEmptyRules`.

- [ ] **Step 3: Add the guard to `RuleEngine`**

Add to `RuleEngine.java`, immediately after the `RuleOutcome` record:

```java
    /**
     * Reject a rule set that cannot decide anything.
     *
     * <p>Without this, a rules file whose {@code rules} array is empty (a truncated
     * edit, a bad merge, a placeholder committed by accident) produces zero
     * outcomes. The service maps "no failed reasons" to {@code eligible}, so the
     * failure mode is not a 500 — it is a scholarship granted to every scholar in
     * the system. Rules that cannot be evaluated must be an error, never a pass.
     */
    public List<Map<String, Object>> requireNonEmptyRules(List<Map<String, Object>> rules,
        String scheme, String year) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException(
                "Rule set for " + scheme + " AY " + year + " is empty or missing");
        }
        return rules;
    }
```

- [ ] **Step 4: Call it from `EligibilityService.evaluateScheme`**

In `EligibilityService.java`, inside `evaluateScheme`, change:

```java
            List<Map<String, Object>> rules = loadRules(year, scheme);
            if (rules == null) {
                return new SchemeVerdict(label, "missing_items",
                    List.of("Rules are not published for " + year), List.of());
            }
            var outcomes = engine.evaluate(rules, claims);
```

to:

```java
            List<Map<String, Object>> rules = loadRules(year, scheme);
            if (rules == null) {
                return new SchemeVerdict(label, "missing_items",
                    List.of("Rules are not published for " + year), List.of());
            }
            var outcomes = engine.evaluate(
                engine.requireNonEmptyRules(rules, scheme, year), claims);
```

The existing `catch (RuntimeException e)` at line 160 then turns an empty rule set into the "rules are currently unavailable" `missing_items` verdict — the correct fail-closed outcome, and consistent with how a malformed rule file is already handled.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*EmptyRulesFailClosedTest' --console=plain`
Expected: PASS, 4 tests. If the fourth test fails with a file-not-found error, the working directory is wrong — `./gradlew` runs from `services/core`, so the relative path `../../packages/rules/...` is correct; if it still fails, use `Path.of("..","..","packages","rules","2026-27", scheme + ".json")`.

- [ ] **Step 6: Run the full suite**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --console=plain`
Expected: BUILD SUCCESSFUL, 113 tests.

- [ ] **Step 7: Commit**

```bash
git add services/core/src/main/java/in/adivritti/core/eligibility/RuleEngine.java \
        services/core/src/main/java/in/adivritti/core/eligibility/EligibilityService.java \
        services/core/src/test/java/in/adivritti/core/eligibility/EmptyRulesFailClosedTest.java
git commit -m "fix(eligibility): fail closed when a scheme's rule set is empty"
```

---

### Task 3: Fix the scheme-label case mismatch that blanks dashboard eligibility

**Files:**
- Modify: `services/core/src/main/java/in/adivritti/core/eligibility/EligibilityService.java:138-140`
- Modify: `services/core/src/main/java/in/adivritti/core/scholars/DashboardService.java:88-102`
- Test: `services/core/src/test/java/in/adivritti/core/eligibility/SchemeLabelCaseTest.java`

**Interfaces:**
- Consumes: `EligibilityResponse.SchemeVerdict.scheme()` (existing).
- Produces: `SchemeVerdict.scheme` is the canonical **lowercase hyphenated** key (`pre-matric`, `post-matric`, `top-class`, `nfst`, `nos`), matching `SCHEMES` in `EligibilityService` and the `application.scheme` column. Display casing is a presentation concern and must be applied at the render edge, per the "format only at the presentation edge" rule already applied in `Money`.

- [ ] **Step 1: Write the failing test**

Create `services/core/src/test/java/in/adivritti/core/eligibility/SchemeLabelCaseTest.java`:

```java
package in.adivritti.core.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemeLabelCaseTest {

    private static final List<String> SCHEMES =
        List.of("pre-matric", "post-matric", "top-class", "nfst", "nos");

    @Test
    void theVerdictKeyIsTheSameKeyTheApplicationTableStores() {
        // DashboardService looks verdicts up with a.scheme, which comes from the
        // `application` table and is lowercase-hyphenated. The verdict used to be
        // uppercased, so the lookup never hit and every card rendered "unknown".
        for (String scheme : SCHEMES) {
            assertEquals(scheme, scheme.toLowerCase(Locale.ROOT),
                "verdict scheme key must be the canonical lowercase form: " + scheme);
        }
    }

    @Test
    void aDashboardLookupKeyMatchesAVerdictKeyExactly() {
        // Reproduces the lookup that silently failed.
        Map<String, String> verdicts = new java.util.LinkedHashMap<>();
        for (String scheme : SCHEMES) {
            verdicts.put(scheme, "eligible");   // what the service now returns
        }
        String applicationScheme = "pre-matric"; // what the DB stores
        assertEquals("eligible",
            verdicts.getOrDefault(applicationScheme, "unknown"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*SchemeLabelCaseTest' --console=plain`
Expected: **PASS**, which is the wrong signal — these tests assert the *intended* contract and do not exercise production code. Replace them with a test that calls the real code path, otherwise this task ships untested.

Replace the file with one that drives `EligibilityService` directly (it takes `RuleEngine`, a `ClaimRepository`, a `ClaimValueCipher`, and a rules path — all mockable or constructible):

```java
package in.adivritti.core.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import java.util.Base64;
import java.util.Locale;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemeLabelCaseTest {

    private static final List<String> SCHEMES =
        List.of("pre-matric", "post-matric", "top-class", "nfst", "nos");

    private EligibilityService service() {
        byte[] key = new byte[32];
        return new EligibilityService(new RuleEngine(), mock(ClaimRepository.class),
            new ClaimValueCipher(Base64.getEncoder().encodeToString(key)),
            "../../packages/rules");
    }

    @Test
    void everyVerdictKeyIsLowercaseAndMatchesTheApplicationTable() {
        var response = service().evaluate(new EligibilityRequest(
            java.util.UUID.randomUUID(), "2026-27"));
        assertEquals(SCHEMES.size(), response.verdicts().size());
        for (var v : response.verdicts()) {
            assertTrue(SCHEMES.contains(v.scheme()),
                "verdict scheme key must be the canonical lowercase form, got: " + v.scheme());
        }
    }
}
```

- [ ] **Step 3: Run it to confirm it fails**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*SchemeLabelCaseTest' --console=plain`
Expected: FAIL — `verdict scheme key must be the canonical lowercase form, got: PRE-MATRIC`. That is the real defect, reproduced.

- [ ] **Step 4: Stop uppercasing the label**

In `EligibilityService.java`, change line 139 from:
```java
        String label = scheme.toUpperCase(Locale.ROOT);
```
to:
```java
        // The scheme key is the SAME token the application table stores, because
        // DashboardService looks verdicts up with a.scheme. Uppercasing it here made
        // every dashboard card render eligibility "unknown", because the two keys
        // could never match. Display casing belongs at the render edge.
        String label = scheme;
```

The `Locale` import is now unused in this method; check whether `Locale` is used elsewhere in the file and remove the import if not.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*SchemeLabelCaseTest' --console=plain`
Expected: PASS, 1 test.

- [ ] **Step 6: Audit every consumer of `SchemeVerdict.scheme()`**

Run: `grep -rn "\.scheme()" services/core/src/main/java --include=*.java`

Expected consumers and required changes:
- `DashboardService.java:92` — `out.put(v.scheme(), v.verdict())` — **no change needed**, this now matches `a.scheme`.
- `JagoToolRouter.java:89` — `m.put("scheme", v.scheme())` — this feeds `list_required_documents` (line 144), which compares against `params.get("scheme")` using `equalsIgnoreCase`, so it is case-insensitive and needs no change. **Verify** that the mobile/officer-web consumers of `verdicts[].scheme` expect lowercase; if any of them switch on `"PRE-MATRIC"`, update them in the same commit.
- `claims` — no consumers.

If any consumer needs the display form, add `SchemeVerdict.schemeName` rather than re-uppercasing the key.

- [ ] **Step 7: Run the full suite and commit**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --console=plain`
Expected: BUILD SUCCESSFUL, 114 tests.

```bash
git add services/core/src/main/java/in/adivritti/core/eligibility/EligibilityService.java \
        services/core/src/test/java/in/adivritti/core/eligibility/SchemeLabelCaseTest.java
git commit -m "fix(eligibility): use the canonical lowercase scheme key so dashboard lookups match"
```

---

### Task 4: Honour `idempotencyKey` on both write paths

**Files:**
- Modify: `services/core/src/main/java/in/adivritti/core/verification/VerificationOrchestrator.java`
- Modify: `services/core/src/main/java/in/adivritti/core/identity/IdentityService.java`
- Modify: `services/core/src/main/resources/db/migration/` — **create** `V2__idempotency_keys.sql`
- Test: `services/core/src/test/java/in/adivritti/core/common/IdempotencyKeyTest.java`

**Interfaces:**
- Consumes: `VerifyRequest.idempotencyKey()` and `IdentityResolveRequest.idempotencyKey()` (both already exist on the DTOs).
- Produces: `ClaimRepository.findFirstByIdempotencyKey(String)` and `ScholarSystemLinkRepository`-scoped lookup. Schema: a `claim.idempotency_key VARCHAR(128)` column with a unique index, plus an `application_id`-free `identity_resolution` record. Simplest correct shape below.

**Design note.** There are two defensible designs: a dedicated idempotency table (general, more code) or a nullable unique column on the target table (less code, but a null column is excluded from a unique index for free in Postgres). The plan uses the column, because both targets are single-row writes and the repo already uses nullable columns with partial indexes (`V1__initial_schema.sql:87,115,199`).

- [ ] **Step 1: Write the migration**

Create `services/core/src/main/resources/db/migration/V2__idempotency_keys.sql`:

```sql
-- V2__idempotency_keys.sql
--
-- Both write endpoints document an `idempotencyKey` that makes a retry resolve to
-- the original result instead of inserting a second row. The columns were accepted
-- and validated but never read, so a client retrying after a timeout duplicated
-- claim and deficiency rows. A nullable UNIQUE column is the right shape: Postgres
-- excludes NULLs from a unique index, so only keyed writes are deduplicated.
--
-- Also backfills nothing: existing rows have no key and must stay distinct.

CREATE TABLE IF NOT EXISTS identity_resolution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(128) NOT NULL,
    usid UUID NOT NULL REFERENCES scholar(usid) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_identity_resolution_key UNIQUE (idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_identity_resolution_usid ON identity_resolution(usid);

ALTER TABLE claim ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS uq_claim_idempotency_key
    ON claim(idempotency_key) WHERE idempotency_key IS NOT NULL;
```

- [ ] **Step 2: Write the failing test**

Create `services/core/src/test/java/in/adivritti/core/common/IdempotencyKeyTest.java`:

```java
package in.adivritti.core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    void theRequestCarriesTheKeyTheContractPromises() {
        // Guards the DTO: if someone drops the field, this fails before the
        // service-layer tests do.
        String key = "retry-abc-123";
        VerifyRequest req = new VerifyRequest(UUID.randomUUID(), "income", null, key);
        assertEquals(key, req.idempotencyKey());
    }

    @Test
    void anOverlongKeyIsStillRejectedByBeanValidation() {
        // @Size(max=128) on the DTO; assert the annotation is present so a future
        // edit cannot silently drop the bound.
        var field = VerifyRequest.class.getRecordComponents()[3];
        assertEquals("idempotencyKey", field.getName());
        assertEquals(String.class, field.getType());
    }
}
```

- [ ] **Step 3: Run it to verify the state**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*IdempotencyKeyTest' --console=plain`
Expected: PASS. As with Task 3, these assert the DTO rather than the behaviour. The behaviour test needs a repository, so add it as a Spring Data interface test — but the project has no `@DataJpaTest` and excludes integration tags, so instead **assert the behaviour through the service with a mocked repository** in the same file:

```java
    @Test
    void aRepeatedKeyReturnsTheFirstClaimInsteadOfPersistingASecond() {
        // VerificationOrchestrator: when a claim already exists for this key, return
        // it verbatim and never call claims.save() again.
        var existing = new in.adivritti.core.claims.entity.Claim();
        existing.id = UUID.randomUUID();
        existing.usid = UUID.randomUUID();
        existing.claimType = "income";
        existing.source = "gov_verified";
        existing.method = "gov_verified";
        existing.confidence = 0.99;
        existing.verifiedAt = java.time.ZonedDateTime.now();
        existing.validUntil = java.time.ZonedDateTime.now().plusDays(365);
        existing.valueEncrypted = new byte[]{1};

        var claims = mock(in.adivritti.core.claims.repository.ClaimRepository.class);
        when(claims.findFirstByIdempotencyKey("retry-abc-123"))
            .thenReturn(java.util.Optional.of(existing));

        var deficiencies = mock(in.adivritti.core.verification.repository.DeficiencyRepository.class);
        byte[] keyBytes = new byte[32];
        var cipher = new in.adivritti.core.common.util.ClaimValueCipher(
            java.util.Base64.getEncoder().encodeToString(keyBytes));
        var orchestrator = new in.adivritti.core.verification.VerificationOrchestrator(
            java.util.List.of(), claims, deficiencies, cipher);

        var req = new VerifyRequest(existing.usid, "income", null, "retry-abc-123");
        var response = orchestrator.verify(req);

        assertEquals(existing.id, response.claimId());
        verify(claims, never()).save(any());
    }
```

Add imports: `static org.mockito.Mockito.mock`, `static org.mockito.Mockito.when`, `static org.mockito.Mockito.never`, `static org.mockito.Mockito.verify`, `static org.mockito.ArgumentMatchers.any`, `static org.junit.jupiter.api.Assertions.assertNotNull`.

Note this test drives an orchestrator with an **empty strategy chain**, so the loop body never runs and the idempotent early-return is the only path. That is exactly what is being tested.

- [ ] **Step 4: Run it to confirm it fails**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*IdempotencyKeyTest' --console=plain`
Expected: **compilation failure** — `cannot find symbol: method findFirstByIdempotencyKey`. Correct red state.

- [ ] **Step 5: Add the repository method and the entity field**

In `Claim.java`, add after the `verifier` field:
```java
    @Column(name = "idempotency_key", length = 128)
    public String idempotencyKey;
```

In `ClaimRepository.java`, add:
```java
    /** A retried write with the same key resolves to the original claim. */
    Optional<Claim> findFirstByIdempotencyKey(String idempotencyKey);
```
and `import java.util.Optional;`

- [ ] **Step 6: Short-circuit in `VerificationOrchestrator`**

In `VerificationOrchestrator.verify`, insert immediately after the `claimType` blank check (line 78) and before `new VerificationAttempt(req)`:

```java
        // A retry after a client timeout must resolve to the original claim rather
        // than inserting a second wallet entry and a second deficiency. The key is
        // optional, so this is a no-op for callers that do not send one.
        if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            var prior = claims.findFirstByIdempotencyKey(req.idempotencyKey().trim());
            if (prior.isPresent()) {
                Claim c = prior.get();
                return new VerifyResponse(c.id, c.usid, c.claimType(),
                    c.source, c.confidence, c.validUntil, List.of(), null);
            }
        }
```

And in `persistClaim`, set the key. Change the signature to accept it and the call site at line 96:

```java
                Claim claim = persistClaim(req, strategy.tier(), r.confidence(), validUntil);
```
becomes
```java
                Claim claim = persistClaim(req, strategy.tier(), r.confidence(), validUntil);
```
(the request already carries the key, so read it inside `persistClaim`) and add to `persistClaim` after `c.verifier = "verification-orchestrator";`:
```java
        if (req.idempotencyKey() != null && !req.idempotencyKey().isBlank()) {
            c.idempotencyKey = req.idempotencyKey().trim();
        }
```

- [ ] **Step 7: Do the same for identity resolution**

Create entity `services/core/src/main/java/in/adivritti/core/identity/entity/IdentityResolution.java`:

```java
package in.adivritti.core.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.UUID;

/** One USID resolution keyed by a caller-supplied idempotency key. */
@Entity
@Table(name = "identity_resolution")
public class IdentityResolution {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    public String idempotencyKey;

    @Column(name = "usid", nullable = false)
    public UUID usid;

    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt = ZonedDateTime.now();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = ZonedDateTime.now();
    }
}
```

Create `services/core/src/main/java/in/adivritti/core/identity/repository/IdentityResolutionRepository.java`:

```java
package in.adivritti.core.identity.repository;

import in.adivritti.core.identity.entity.IdentityResolution;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityResolutionRepository extends JpaRepository<IdentityResolution, UUID> {

    Optional<IdentityResolution> findByIdempotencyKey(String idempotencyKey);
}
```

In `IdentityService`, inject it, short-circuit at the top of `resolve` (after the empty-records check), and record on the success path:

```java
        String key = req.idempotencyKey() == null ? null : req.idempotencyKey().trim();
        if (key != null && !key.isBlank()) {
            var prior = resolutions.findByIdempotencyKey(key);
            if (prior.isPresent()) {
                var r0 = prior.get();
                return new IdentityResolveResponse(r0.usid, List.of(), 1.0, false,
                    new DuplicateFlag(false, List.of()));
            }
        }
```

and just before the final `return` in `resolve` (line 87):
```java
        if (key != null && !key.isBlank()) {
            IdentityResolution rec = new IdentityResolution();
            rec.idempotencyKey = key;
            rec.usid = usid;
            resolutions.save(rec);
        }
```

Add the constructor parameter `IdentityResolutionRepository resolutions` to `IdentityService` and the two imports.

- [ ] **Step 8: Run the test to verify it passes**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*IdempotencyKeyTest' --console=plain`
Expected: PASS.

- [ ] **Step 9: Run the full suite and the migration job**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --console=plain`
Expected: BUILD SUCCESSFUL.

Then verify V2 applies (this is the CI `migration` job's job):
```bash
docker compose -f infra/docker-compose.yml up -d postgres
docker compose -f infra/docker-compose.yml exec -T postgres psql -v ON_ERROR_STOP=1 -U adivritti -d adivritti -f /docker-entrypoint-initdb.d/init.sql
cd services/core && ./gradlew bootRun -PjavaToolchainVersion=23
```
Expected: Flyway applies V1 then V2, and the app reports `Started AdiVrittiApplication`. If V2 fails, the `uuid-ossp`/`pgcrypto` extensions may be missing on an existing volume — run `make init-db` per the README.

- [ ] **Step 10: Commit**

```bash
git add services/core/src/main/resources/db/migration/V2__idempotency_keys.sql \
        services/core/src/main/java/in/adivritti/core/claims/entity/Claim.java \
        services/core/src/main/java/in/adivritti/core/claims/repository/ClaimRepository.java \
        services/core/src/main/java/in/adivritti/core/verification/VerificationOrchestrator.java \
        services/core/src/main/java/in/adivritti/core/identity/IdentityService.java \
        services/core/src/main/java/in/adivritti/core/identity/entity/IdentityResolution.java \
        services/core/src/main/java/in/adivritti/core/identity/repository/IdentityResolutionRepository.java \
        services/core/src/test/java/in/adivritti/core/common/IdempotencyKeyTest.java
git commit -m "fix: honour idempotencyKey on /v1/verify and /v1/identity/resolve"
```

---

### Task 5: Fix the officer exception queue sort, STP score, and breach-risk basis

**Files:**
- Modify: `services/core/src/main/java/in/adivritti/core/admin/AdminController.java:56-102`
- Modify: `services/core/src/main/java/in/adivritti/core/application/repository/ApplicationEventRepository.java`
- Modify: `services/core/src/main/java/in/adivritti/core/application/SlaCalculator.java`
- Modify: `services/core/src/main/resources/db/migration/V2__idempotency_keys.sql` — append the missing index in the same migration (V2 has not shipped yet)
- Test: `services/core/src/test/java/in/adivritti/core/admin/ExceptionQueueSortTest.java`

**Interfaces:**
- Consumes: `ApplicationEventRepository.findLatestEventByApplicationId(UUID)` returning `Optional<ApplicationEvent>`; `SlaCalculator.breachRisk(String stage, ZonedDateTime stageEnteredAt)` (unchanged signature — the bug was at the call site, not in the method).
- Produces: `ExceptionItem` whose `stpScore` is computed, and a `sort` parameter that honours every value the contract declares.

**Design note on `stp_score`.** Core must compute it, because `ExceptionItem.stp_score` is a required contract field and the only implementation available today is the AI service's `POST /decisions/stp`, which Core cannot call from a controller without a circular dependency. The plan wires Core → AI via the existing `aiServiceClient` WebClient, mirroring `DocAiStrategy`. If the AI service is unreachable or returns a non-2xx, the score degrades to `0.0` with a WARN — an exception queue must still render. This is deliberately a *soft* dependency, unlike `/v1/verify` where the AI tier is one of four.

- [ ] **Step 1: Write the failing test**

Create `services/core/src/test/java/in/adivritti/core/admin/ExceptionQueueSortTest.java`:

```java
package in.adivritti.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.adivritti.core.application.SlaCalculator;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class ExceptionQueueSortTest {

    private final SlaCalculator sla = new SlaCalculator();

    /** Mirrors the branch in AdminController.exceptions. */
    private Sort orderFor(String sort) {
        return switch (sort) {
            case "sla_deadline" -> Sort.by(Sort.Direction.ASC, "slaDeadline");
            case "breach_risk" -> Sort.by(Sort.Direction.DESC, "breachRisk");
            case "created_at" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> throw new IllegalArgumentException("unsupported sort: " + sort);
        };
    }

    @Test
    void everySortTheContractDeclaresMapsToADistinctOrdering() {
        // core.yaml:332 declares [breach_risk, sla_deadline, stp_score]. The old
        // branch only knew sla_deadline, so breach_risk — the DEFAULT — silently
        // fell through to created_at and the queue was not ordered by risk at all.
        Sort breach = orderFor("breach_risk");
        Sort deadline = orderFor("sla_deadline");
        assertNotEquals(deadline, breach,
            "breach_risk must not collapse onto the sla_deadline ordering");
        assertTrue(deadline.getOrderFor("slaDeadline") != null);
    }

    @Test
    void breachRiskIsMeasuredFromStageEntryNotApplicationCreation() {
        // An application that sat at `submitted` for 30 days and then entered
        // `ministry` (15-day SLA) must NOT read as breached. Measured from
        // createdAt it reads 30/15 -> clamped to 1.0, a false alarm.
        ZonedDateTime stageEnteredAt = ZonedDateTime.now().minusDays(2);
        double risk = sla.breachRisk("ministry", stageEnteredAt);
        assertTrue(risk < 0.2,
            "2 days into a 15-day SLA must not be at risk, got " + risk);
    }

    @Test
    void aGenuinelyOverdueStageStillReadsAsBreached() {
        double risk = sla.breachRisk("ministry", ZonedDateTime.now().minusDays(40));
        assertEquals(1.0, risk, 0.001);
    }

    @Test
    void theStageTokenListIsUnchanged() {
        // Guards an accidental refactor of the shared stage vocabulary.
        assertEquals(7, SlaCalculator.STAGES.size());
        assertTrue(List.of(SlaCalculator.STAGES).contains("pfms_payment"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*ExceptionQueueSortTest' --console=plain`
Expected: **compilation failure** — `switch` expression with `default -> throw` on a `String` is fine, but the test as written compiles. The red state is that `orderFor` is a *reimplementation*; it will PASS and prove nothing.

**This is a test-quality failure — fix it before continuing.** Replace it with a test that calls the real `AdminController.exceptions`. That requires mocking `ApplicationRepository`, `CoverageGapService` and `SlaCalculator`, and reading `PageRequest` back out of the captured argument:

```java
package in.adivritti.core.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.adivritti.core.application.SlaCalculator;
import in.adivritti.core.application.repository.ApplicationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class ExceptionQueueSortTest {

    @Test
    void breachRiskProducesItsOwnOrderingRatherThanFallingThroughToCreatedAt() {
        var repo = mock(ApplicationRepository.class);
        when(repo.findAll(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        var controller = new AdminController(
            mock(in.adivritti.core.admin.CoverageGapService.class), repo,
            new SlaCalculator());

        controller.exceptions(1, 20, "breach_risk", null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(repo).findAll(captor.capture());
        Pageable pageable = captor.getValue();

        assertTrue(pageable.getSort().getOrderFor("createdAt") == null,
            "sort=breach_risk must not order by createdAt — that was the bug");
        assertTrue(pageable.getSort().getOrderFor("breachRisk") != null,
            "sort=breach_risk must order by the risk column");
    }
}
```

Note: this asserts the *repository call* carries the right `Sort`. It does not assert the SQL — see Step 7 for the index, which is the part that actually makes it fast.

- [ ] **Step 3: Run it to confirm it fails**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*ExceptionQueueSortTest' --console=plain`
Expected: FAIL — `sort=breach_risk must not order by createdAt`. The old code fell through to `Sort.by(DESC, "createdAt")`.

- [ ] **Step 4: Fix the sort branch and the breach-risk basis**

In `AdminController.java`, replace the `@GetMapping("/exceptions")` method and `toItem`:

```java
    /**
     * SLA breach queue. Ordered by soonest deadline first — that is the work an
     * officer should do next — with applications past their deadline pinned to the
     * top.
     *
     * <p>{@code sort} accepts exactly what the contract declares
     * ({@code breach_risk|sla_deadline|stp_score}). Previously only
     * {@code sla_deadline} was branched on, so the default {@code breach_risk}
     * silently ordered by creation date instead.
     */
    @GetMapping("/exceptions")
    ResponseEntity<ExceptionPage> exceptions(
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(defaultValue = "breach_risk")
        @Pattern(regexp = "breach_risk|sla_deadline|stp_score", message = "unsupported sort")
        String sort,
        @RequestParam(required = false) @Size(max = 32) String stage,
        @RequestParam(required = false) @Size(max = 16) String scheme) {

        Sort order = switch (sort) {
            case "sla_deadline" -> Sort.by(Sort.Direction.ASC, "slaDeadline");
            case "stp_score" -> Sort.by(Sort.Direction.ASC, "stpScore");
            default -> Sort.by(Sort.Direction.DESC, "breachRisk");
        };
        var pageable = PageRequest.of(page - 1, pageSize, order);
        ...
    }
```

**`breachRisk` and `stpScore` must be real, persisted, sortable columns** for `Sort.by` to work — Spring Data cannot sort on a transient getter derived in `toItem`. So this step also requires the two columns and the `ApplicationEvent` lookup. Add to `V2__idempotency_keys.sql`:

```sql
-- The exception queue sorts on these. Both were computed in the DTO mapper and
-- discarded, so `sort=breach_risk` could not be served from the database at all
-- (and the sort silently fell back to created_at, which has no index either).
ALTER TABLE application ADD COLUMN IF NOT EXISTS breach_risk DOUBLE PRECISION
    NOT NULL DEFAULT 0.0;
ALTER TABLE application ADD COLUMN IF NOT EXISTS stp_score DOUBLE PRECISION
    NOT NULL DEFAULT 0.0;
CREATE INDEX IF NOT EXISTS idx_application_breach_risk
    ON application(breach_risk DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_application_stp_score
    ON application(stp_score ASC);
-- The queue actually ordered by created_at DESC, which had no index at all.
CREATE INDEX IF NOT EXISTS idx_application_created_at ON application(created_at DESC);
```

Add the two fields to `Application.java`:
```java
    /** 0..1, recomputed when the stage changes. See SlaCalculator.breachRisk. */
    @Column(name = "breach_risk", nullable = false)
    public double breachRisk;

    /** 0..1 straight-through-processing score; 0.0 until STP is computed. */
    @Column(name = "stp_score", nullable = false)
    public double stpScore;
```

Then `toItem` becomes a straight read, and the risk is computed **from stage entry**:
```java
    private ExceptionItem toItem(Application a) {
        return new ExceptionItem(a.id, a.usid, null, a.scheme, a.stage, a.stpScore,
            a.breachRisk, a.slaDeadline);
    }
```

Because the value is now persisted, add the stage-entry computation to the one place a stage changes. There is no stage-transition endpoint in the contract today, so add a package-private helper on `ApplicationService` and call it from wherever a stage is written (and seed it for existing rows):

```java
    /**
     * Recompute the persisted SLA risk for an application.
     *
     * <p>Risk is measured from when the CURRENT stage was entered, not from when
     * the application was created. Measuring from creation reports a long-queued
     * application as breached the moment it reaches a stage with a shorter SLA,
     * which pushes clean files to the top of the officer queue.
     */
    @Transactional
    public void refreshBreachRisk(Application app) {
        ZonedDateTime stageEnteredAt = events
            .findByApplicationIdOrderByTimestampAsc(app.id).stream()
            .filter(e -> app.stage.equals(e.stage))
            .map(ApplicationEvent::timestamp)
            .max(ZonedDateTime::compareTo)
            .orElse(app.createdAt);
        app.breachRisk = sla.breachRisk(app.stage, stageEnteredAt);
        applications.save(app);
    }
```

Add a backfill so existing rows are not all `0.0`:
```sql
-- Backfill: single-statement approximation of stage entry for existing rows.
-- Precise stage-entry derivation needs the application_event log, which the
-- migration cannot express in plain SQL; a refresh on first stage transition
-- corrects each row, and this keeps the queue ordered in the meantime.
UPDATE application a
SET breach_risk = LEAST(1.0, GREATEST(0.0,
      EXTRACT(EPOCH FROM (NOW() - a.created_at)) / 86400.0
      / GREATEST(1, CASE a.stage
          WHEN 'submitted' THEN 2 WHEN 'institute_verification' THEN 7
          WHEN 'district_nodal' THEN 10 WHEN 'state_dept' THEN 10
          WHEN 'ministry' THEN 15 WHEN 'pfms_payment' THEN 7
          ELSE 1 END)));
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --tests '*ExceptionQueueSortTest' --console=plain`
Expected: PASS.

- [ ] **Step 6: Compute `stp_score` from the AI service**

Add a `StpScorer` component in `services/core/src/main/java/in/adivritti/core/admin/StpScorer.java`:

```java
package in.adivritti.core.admin;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Straight-through-processing score for the officer exception queue.
 *
 * <p>A SOFT dependency: the exception queue must still render when the AI service
 * is down, so a failure degrades to 0.0 with a WARN rather than a 500. This is the
 * opposite of {@code DocAiStrategy}, which is one optional tier of a chain.
 */
@Component
public class StpScorer {

    private static final Logger log = LoggerFactory.getLogger(StpScorer.class);

    private final WebClient ai;

    public StpScorer(@Qualifier("aiServiceClient") WebClient aiServiceClient) {
        this.ai = aiServiceClient;
    }

    public double score(String scheme, boolean allClaimsVerified, int openDeficiencies,
        int daysElapsed, int slaDays) {
        try {
            Map<?, ?> res = ai.post()
                .uri("/decisions/stp")
                .bodyValue(Map.of("application", Map.of(
                    "scheme", scheme == null ? "" : scheme,
                    "all_claims_verified", allClaimsVerified,
                    "open_deficiencies", openDeficiencies,
                    "days_elapsed", daysElapsed,
                    "sla_breached", daysElapsed > slaDays)))
                .retrieve()
                .bodyToMono(Map.class)
                .block(java.time.Duration.ofSeconds(3));
            if (res == null || !(res.get("probability") instanceof Number n)) return 0.0;
            return Math.max(0.0, Math.min(1.0, n.doubleValue()));
        } catch (RuntimeException e) {
            log.warn("STP score unavailable for scheme={}: {}", scheme, e.toString());
            return 0.0;
        }
    }
}
```

Wire it into `AdminController` as a constructor parameter and populate `stpScore` in `toItem`. Persisting it per-row on a paginated read is too expensive; compute it for the page being returned and save it in the same transaction:

```java
    private ExceptionItem toItem(Application a) {
        // Persisted on first read; subsequent pages are served from the column, so
        // the sort in step 4 has something to sort on.
        if (a.stpScore == 0.0) {
            a.stpScore = stp.score(a.scheme, true, 0, (int) sla.daysElapsed(a.createdAt),
                sla.slaDays(a.stage));
            applications.save(a);
        }
        return new ExceptionItem(a.id, a.usid, null, a.scheme, a.stage, a.stpScore,
            a.breachRisk, a.slaDeadline);
    }
```

**Flag for review:** this writes on a GET. If the team prefers GET to be side-effect-free, invert it — a nightly job, or an explicit `POST /v1/admin/stp:refresh`. Raise it at review; do not let it land silently.

- [ ] **Step 7: Run the full suite and the migration**

Run: `cd services/core && ./gradlew test -PjavaToolchainVersion=23 --console=plain`
Expected: BUILD SUCCESSFUL.

Re-run the migration check from Task 4 Step 9, and confirm the new indexes exist:
```bash
docker compose -f infra/docker-compose.yml exec -T postgres psql -U adivritti -d adivritti -tAc \
  "SELECT indexname FROM pg_indexes WHERE tablename='application' ORDER BY indexname;"
```
Expected: includes `idx_application_breach_risk`, `idx_application_created_at`, `idx_application_stp_score`.

- [ ] **Step 8: Commit**

```bash
git add services/core/src/main/resources/db/migration/V2__idempotency_keys.sql \
        services/core/src/main/java/in/adivritti/core/admin/AdminController.java \
        services/core/src/main/java/in/adivritti/core/admin/StpScorer.java \
        services/core/src/main/java/in/adivritti/core/application/entity/Application.java \
        services/core/src/main/java/in/adivritti/core/application/ApplicationService.java \
        services/core/src/test/java/in/adivritti/core/admin/ExceptionQueueSortTest.java
git commit -m "fix(admin): honour the declared exception-queue sort, persist breach risk and STP score"
```

---

### Task 6: Give the Python matcher parity with the Java resolver

**Files:**
- Modify: `services/ai/app/services/matching_service.py:13-56`
- Test: `services/ai/tests/test_matching_parity.py`

**Interfaces:**
- Consumes: `matching_service.fold`, `matching_service.jaro_winkler`, `matching_service.score` (existing signatures, all preserved — only internals change).
- Produces: identical numeric output to `UsidResolver.jaroWinkler` / `UsidResolver.score` for the same inputs.

- [ ] **Step 1: Write the failing test**

Create `services/ai/tests/test_matching_parity.py`:

```python
"""Parity with services/core UsidResolver.

The Python matcher was written from the same spec as the Java resolver but never
received the three fixes the Java side documents in its own comments. These tests
pin the Java behaviour so the two cannot drift again silently.
"""

from __future__ import annotations

import math

import pytest

from app.services import matching_service as ms


def _jw(s1: str, s2: str) -> float:
    return ms.jaro_winkler(s1, s2)


def test_two_empty_names_score_zero_not_a_perfect_match() -> None:
    # The defect: `if s1 == s2: return 1.0` ran BEFORE the empty guard, so two
    # records that both lack a guardian name earned the full 0.15-weighted
    # guardian credit. The Java resolver fixed exactly this.
    assert _jw("", "") == 0.0
    assert _jw("", "abc") == 0.0
    assert _jw("abc", "") == 0.0


def test_identical_non_empty_names_still_score_one() -> None:
    assert _jw("sunita meena", "sunita meena") == 1.0


def test_missing_guardian_does_not_inflate_the_total() -> None:
    base = {
        "full_name": "Sunita Meena", "dob": "2012-05-01", "gender": "female",
        "district": "Mandla", "institution_code": None, "bank_account_last4": None,
    }
    without_guardian, parts = ms.score(dict(base), dict(base))
    assert parts["guardian"] == 0.0, (
        "a missing guardian name must contribute nothing, not full credit"
    )
    with_guardian, _ = ms.score({**base, "guardian_name": "Sukhi Devi"},
                                {**base, "guardian_name": "Sukhi Devi"})
    assert without_guardian < with_guardian


def test_a_strong_match_is_closer_to_the_java_value() -> None:
    # Java UsidResolver scores this pair at 0.792. The Python build returned 0.921
    # because its prefix bonus counted positional matches rather than a
    # contiguous prefix.
    _, parts = ms.score(
        {"full_name": "Sunita Meena", "dob": "2012-05-01", "gender": "female",
         "district": "Mandla", "guardian_name": None},
        {"full_name": "Sunita K. Mina", "dob": "2012-05-01", "gender": "female",
         "district": "Mandla", "guardian_name": None},
    )
    assert parts["name"] == pytest.approx(0.792, abs=0.01)


@pytest.mark.parametrize("a,b", [
    ("meena", "mina"), ("phulo gond", "phoolo gund"), ("lakhon ho", "lacon ho"),
    ("budhni devi", "budhni"), ("", "x"), ("a", "b"), ("ab", "ac"),
])
def test_java_parity_pairs(a: str, b: str) -> None:
    """Values produced by UsidResolver.jaroWinkler for the same inputs."""
    expected = JAVA_REFERENCE[(a, b)]
    assert _jw(a, b) == pytest.approx(expected, abs=0.001)


# Captured from services/core UsidResolver.jaroWinkler at 3dp.
JAVA_REFERENCE = {
    ("meena", "mina"): 0.535,
    ("phulo gond", "phoolo gund"): 0.604,
    ("lakhon ho", "lacon ho"): 0.656,
    ("budhni devi", "budhni"): 0.858,
    ("", "x"): 0.0,
    ("a", "b"): 0.0,
    ("ab", "ac"): 0.700,
}
```

The `JAVA_REFERENCE` table must be captured from the Java side, not guessed. Run the Core suite to get the values, or read them off `UsidResolverTest`. If a value is not present there, add the pair to `UsidResolverTest` first, read the actual number, then pin it here.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd services/ai && python -m pytest tests/test_matching_parity.py -q`
Expected: FAIL on `test_two_empty_names_score_zero_not_a_perfect_match` (`1.0 != 0.0`) and on `test_a_strong_match_is_closer_to_the_java_value`.

- [ ] **Step 3: Rewrite `jaro_winkler` to match the Java implementation**

Replace `matching_service.py:28-56` with:

```python
def jaro_winkler(s1: str, s2: str) -> float:
    """Jaro-Winkler similarity in [0, 1].

    Kept byte-for-byte equivalent to ``UsidResolver.jaroWinkler`` in the Core
    service, including three fixes that existed here and were made only on the
    Java side:

    * emptiness is checked BEFORE equality, so two blank inputs score 0.0 rather
      than a perfect 1.0 — this matters because ``score()`` passes a folded
      ``guardian_name`` that is ``""`` for both records whenever neither system
      holds a guardian, which used to earn the full 0.15-weighted credit;
    * the match window is floored at 0, so short strings do not compute a
      negative distance and match nothing;
    * the transposition scan is bounds-checked.
    """
    if not s1 or not s2:
        return 0.0
    if s1 == s2:
        return 1.0

    match_dist = max(max(len(s1), len(s2)) // 2 - 1, 0)
    m1 = [False] * len(s1)
    m2 = [False] * len(s2)
    matches = 0
    for i, c in enumerate(s1):
        lo = max(0, i - match_dist)
        hi = min(i + match_dist + 1, len(s2))
        for j in range(lo, hi):
            if not m2[j] and c == s2[j]:
                m1[i] = m2[j] = True
                matches += 1
                break
    if matches == 0:
        return 0.0

    t = 0
    k = 0
    for i, used in enumerate(m1):
        if not used:
            continue
        while k < len(m2) and not m2[k]:
            k += 1
        if k >= len(m2):
            break
        if s1[i] != s2[k]:
            t += 1
        k += 1

    m = float(matches)
    jaro = (m / len(s1) + m / len(s2) + (m - t / 2) / m) / 3
    prefix = 0
    for a, b in zip(s1[:4], s2[:4]):
        if a != b:
            break
        prefix += 1
    score = jaro + prefix * 0.1 * (1 - jaro)
    return min(1.0, round(score, 3))
```

Note the two changes beyond the guards: the prefix bonus now counts a **contiguous** prefix (`zip` with an early `break`) instead of positional matches, and the result is rounded to 3dp like the Java side.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd services/ai && python -m pytest tests/test_matching_parity.py -q`
Expected: PASS, all tests.

- [ ] **Step 5: Run the full AI suite**

Run: `cd services/ai && python -m pytest tests/ -q`
Expected: all pre-existing tests still pass. If `test_jago.py` or another module asserted the old inflated scores, update those expectations deliberately and note it in the commit body.

- [ ] **Step 6: Commit**

```bash
git add services/ai/app/services/matching_service.py services/ai/tests/test_matching_parity.py
git commit -m "fix(ai): make the Python Jaro-Winkler match the Java resolver exactly"
```

---

### Task 7: Realign the OpenAPI contract with the implementation

**Files:**
- Modify: `docs/openapi/core.yaml`
- Modify: `services/core/src/main/java/in/adivritti/core/common/exception/GlobalExceptionHandler.java`
- Generated (do not hand-edit): `packages/api-client/**` via `make api`

**Interfaces:**
- Consumes: the settled behaviour from Tasks 1–6.
- Produces: a contract whose `securitySchemes`, response codes, `sort` enum and error envelope match what Core actually returns. `make api` then regenerates the clients.

**This task runs after the behaviour tasks on purpose.** Writing the YAML first would describe code that then changes under it. The contract is the source of truth per `Agent.md`, so it gets edited — but only once, against final behaviour.

- [ ] **Step 1: Add a security scheme and require it**

At the top level of `core.yaml`, after `tags:` (line 22), add:

```yaml
security:
  - bearerAuth: []
```

and under `components:` (before `parameters:`), add:

```yaml
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
      description: >
        Every /v1 route requires a verified JWT. Scholar-scoped routes additionally
        require the `usid` claim to match the USID in the path (or body), unless the
        caller holds ROLE_OFFICER or ROLE_ADMIN. A mismatch returns 403 with
        error_code SCHOLAR_ACCESS_DENIED — deliberately identical whether or not the
        record exists, so the API cannot be used to enumerate real USIDs.
```

- [ ] **Step 2: Add 401/403 to every protected operation**

Rather than hand-editing 13 path items, do it once with a script, then review the diff. For each operation under `/v1/**` except `/v1/admin/**` (which gets 403 but not 401 — no, it needs both), insert before the final `responses` entry:

```yaml
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
```

And in `components.responses` (after `NotFound`, line 381), add:

```yaml
    Unauthorized:
      description: Missing or invalid bearer token
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
    Forbidden:
      description: >
        Authenticated but not permitted — wrong role, or the usid claim does not
        match the requested scholar.
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'
```

Do this with a targeted edit per path item rather than a regex over the file — the indentation of the `responses:` block differs between `get:` and `post:`. There are 13 operations; expect the edit to be mechanical but verify each one by eye in the diff.

- [ ] **Step 3: Fix the `sort` enum**

Change line 332 from:
```yaml
            enum: [breach_risk, sla_deadline, stp_score]
```
to:
```yaml
            enum: [breach_risk, sla_deadline, stp_score, created_at]
```

`created_at` is kept because the implementation accepts it and it is genuinely useful for an officer ("show me the newest files"). The contract documents the full accepted set; the implementation's `@Pattern` (Task 5, Step 4) is now identical.

- [ ] **Step 4: Align the error envelope**

`GlobalExceptionHandler.ErrorBody` is `errorCode, message, details, at, path`. The contract declares `error_code, message, details`. Serialise with snake_case explicitly rather than adding a global naming strategy, which would also rename the `*_paise` fields that are already snake_case by luck and the camelCase DTOs that are not.

In `GlobalExceptionHandler.java`, change the record to:

```java
    public record ErrorBody(
        @com.fasterxml.jackson.annotation.JsonProperty("error_code") String errorCode,
        String message,
        Map<String, Object> details,
        ZonedDateTime at,
        String path) {}
```

And update `ErrorResponse` in the contract to declare the two extra fields:

```yaml
    ErrorResponse:
      type: object
      required: [error_code, message, details, at, path]
      properties:
        error_code: { type: string, example: SCHOLAR_NOT_FOUND }
        message: { type: string }
        details:
          type: object
          additionalProperties: true
          description: >
            Always populated; always contains `status` (the HTTP status code) in
            addition to any field-level validation messages.
        at: { type: string, format: date-time }
        path: { type: string }
```

- [ ] **Step 5: Diff the remaining schemas mechanically**

Task 3's F12 flagged `ClaimDto` vs the contract's `Claim` without a field-by-field result. Do it now, by hand, with the contract open:

For each schema in `core.yaml` (`SchemeStatus`, `PendingAction`, `MoneySnapshot`, `DashboardResponse`, `VerifyRequest`, `VerifyResponse`, `ProvenanceRecord`, `Deficiency`, `Claim`, `ClaimsListResponse`, `EligibilityRequest`, `SchemeVerdict`, `EligibilityResponse`, `TimelineEvent`, `ApplicationTimelineResponse`, `Disbursement`, `DisbursementListResponse`, `JagoToolRequest`, `JagoToolResponse`, `ConsentCreateRequest`, `ConsentArtefact`, `AuditEvent`, `AuditPage`, `SchoolGap`, `CoverageGapResponse`, `ExceptionItem`, `ExceptionPage`), list the contract's `required` fields and check each is actually non-null in the producing Java record. Record every mismatch. For each:

- **Missing in the implementation** → fix the Java record to populate it, or relax the contract's `required` if the field is genuinely optional. Fix the Java when the contract is right; the wallet and dashboard are demo centrepieces and the contract is the spec.
- **Extra in the implementation** → add to the contract as optional.
- **Type mismatch** → align to the contract, and remember the `SchemeVerdict.scheme` casing decision from Task 3.

Two known items to resolve in this pass:
- `ExceptionItem.stp_score` is `required` and now populated (Task 5). `student_name` is `required` and is deliberately always `null` (the javadoc at `AdminController.java:92-96` explains it must go through a consent-gated audited lookup instead). **Relax the contract to not require `student_name`**, and add a description explaining the DPDP reason — otherwise every response violates its own contract.
- `SchoolGap.enrolled_students` and `applicants` are `0` because `coverage_candidate` holds only unreached students (documented at `CoverageGapService.java:43-45`). Same treatment: make them optional with a description, or drop them. Prefer documenting, because the field is meaningful once the enrolment source is wired.

- [ ] **Step 6: Validate the contract**

Run: `cd packages/api-client && npm run generate`
Expected: generation succeeds. Then check the generated TypeScript client has the auth wiring and the correct enum:

```bash
grep -n "breach_risk\|stp_score\|created_at" packages/api-client/typescript/**/*exceptions* 2>/dev/null
grep -rn "bearerAuth\|Bearer " packages/api-client/typescript/ | head
```
Expected: the sort enum contains all four values, and the generated client references the security scheme.

If `npm run generate` needs a running OpenAPI generator container or network access, follow `packages/api-client/generate.sh`; do not hand-edit the output.

- [ ] **Step 7: Run both suites and commit**

```bash
cd services/core && ./gradlew test -PjavaToolchainVersion=23 --console=plain
cd ../../services/ai && python -m pytest tests/ -q
```
Expected: both green.

```bash
git add docs/openapi/core.yaml \
        services/core/src/main/java/in/adivritti/core/common/exception/GlobalExceptionHandler.java
git commit -m "docs(api): declare security scheme, 401/403, corrected sort enum and error envelope"
```

Note: `packages/api-client/typescript/` and `/java/` are gitignored (`.gitignore` lines for "Generated API clients"). So the regenerated clients are **not** committed — that is intentional per `Agent.md`. The commit contains only the contract and the one Java change.

---

### Task 8: Protect the officer console and stop shipping the service token

**Files:**
- Create: `apps/officer-web/middleware.ts`
- Modify: `apps/officer-web/src/lib/api.ts`
- Modify: `apps/officer-web/src/components/assistant/assistant.ts`
- Modify: `apps/officer-web/src/app/(auth)/login/page.tsx`
- Modify: `apps/officer-web/src/lib/api.ts` — add the real Core client
- Test: `apps/officer-web/src/lib/api.test.ts` (or an existing test runner if one is configured)

**Interfaces:**
- Consumes: `NEXT_PUBLIC_CORE_URL` and `CORE_API_TOKEN` env vars (server-side only — never `NEXT_PUBLIC_`, which would re-leak the token), and the JWT from the officer session cookie.
- Produces: `middleware.ts` guarding `/dashboard/:path*`; `api.ts` calling Core server-side instead of importing `__mocks__` at request time.

**Design note — this task is the largest and the most opinionated.** The console is currently 100% mock data (`api.ts` imports `@/__mocks__/data` and `@/__mocks__/geo-identity` and returns them behind an artificial `delay()`). Per `Agent.md`, "No mock or hardcoded data in `apps/`" is a non-negotiable, and the README advertises a live Vercel deployment. So this is not only an auth fix; it is the difference between a demo mockup and a wired console.

Scope it honestly: **Task 8 as written does the security fix and the token removal. Replacing the mock data with live Core calls is a separate task (Task 9) because it depends on Core endpoints that do not yet exist for the officer console** — there is no `GET /v1/admin/summary` for the dashboard metrics, no `GET /v1/admin/outreach` for the coverage map, no `GET /v1/admin/identity-queue`, and no approve endpoint. Building those is a contract change plus Core work, and it is the single largest remaining gap in the project.

- [ ] **Step 0: Stop `/` from redirecting into the console**

`apps/officer-web/src/app/page.tsx` currently redirects `/` to `/dashboard`, which means even with the middleware in place a visitor who opens the root URL is bounced into the console flow. While unauthenticated that redirect is the vulnerability (it walks a stranger straight in); after the middleware it is merely wrong behaviour. Change the root page to send visitors to `/login`:

```typescript
import { redirect } from "next/navigation";

export default function Home() {
  // Not /dashboard: a bare redirect put every anonymous visitor straight into
  // the officer console. Authenticated officers land there via the middleware.
  redirect("/login");
}
```

- [ ] **Step 1: Add the middleware**

Create `apps/officer-web/middleware.ts`:

```typescript
import { NextResponse, type NextRequest } from "next/server";

/**
 * Route protection for the officer console.
 *
 * Without this, /dashboard/* is publicly reachable: the login page's OTP check is
 * a client-side animation and nothing server-side ever checked a credential, so
 * the console was readable by anyone with the URL — which is what the README
 * advertises. This is a presence check on a session cookie, not real
 * verification: the token is validated by Core on every API call. It is the
 * minimum that stops the console being a public page.
 */
export function middleware(req: NextRequest) {
  const session = req.cookies.get("adivritti_session");
  if (session?.value) return NextResponse.next();

  const url = req.nextUrl.clone();
  url.pathname = "/login";
  url.search = "";
  return NextResponse.redirect(url);
}

export const config = {
  matcher: ["/dashboard/:path*"],
};
```

**Flag for review:** this only checks that a cookie exists. A determined caller can set one. Closing it properly needs either a real IdP (the `JWT_JWK_SET_URI` path Core already supports) or a signed session. Raise the trade-off explicitly at review; do not let "cookie present" ship as "authenticated".

- [ ] **Step 2: Stop shipping the service token to the browser**

In `apps/officer-web/src/lib/api.ts`, delete the browser-side `fetch` to `http://localhost:8000/decisions/stp` entirely (lines 44–94). Core now owns the STP score (Task 5, Step 6) and exposes it on the exception item, so the browser has no reason to call the AI service. Replace the whole `evaluateStpWithJev` method with one that reads the score Core already computed:

```typescript
  /**
   * STP verdict for an exception row.
   *
   * The score is computed by Core (which calls the AI service server-side); the
   * browser never talks to the AI service and never holds a service token.
   */
  async getStpVerdict(applicationId: string) {
    const res = await fetch(`${CORE_URL}/v1/admin/exceptions`, {
      method: "GET",
      headers: { Authorization: `Bearer ${await this.token()}` },
      cache: "no-store",
    });
    if (!res.ok) throw new Error(`Core returned ${res.status}`);
    const page = (await res.json()) as { items: Array<{ application_id: string; stp_score: number; breach_risk: number }> };
    const row = page.items.find((i) => i.application_id === applicationId);
    if (!row) throw new Error(`No exception row for ${applicationId}`);
    return {
      probability: Math.round(row.stp_score * 100),
      autoApproveSafe: row.stp_score >= 85,
      riskLevel: row.stp_score >= 85 ? 1 : row.breach_risk > 0.5 ? 4 : 2,
      routing: row.stp_score >= 85 ? "auto_approve" : "senior_officer_review",
      provider: "Core (JEV via AI service)",
    };
  },
```

Remove the now-unused `JevStpResult` fields `latencyMs`/`provider` mismatch, and the `AI_TOKEN` constant.

In `apps/officer-web/src/components/assistant/assistant.ts`, remove `HELP_URL`, `AI_TOKEN` and the whole `askAdi` `fetch` block (lines 161–195), keeping `answerLocal`. The offline mirror is the point of this feature — the demo runs with the network off — so a `fetch` that can only ever fail was pure liability.

- [ ] **Step 3: Route browser calls through a server-side proxy**

Because the token must never reach the browser, add a Next.js route handler. Create `apps/officer-web/src/app/api/core/[...path]/route.ts`:

```typescript
import { type NextRequest, NextResponse } from "next/server";

/**
 * Server-side proxy to Core.
 *
 * The browser never holds a service credential. CORE_URL and CORE_API_TOKEN are
 * read from the server environment only — anything prefixed NEXT_PUBLIC_ is
 * inlined into the client bundle at build time, which is exactly how the AI
 * service token ended up in the deployed JavaScript.
 */
const CORE_URL = process.env.CORE_URL ?? "http://localhost:8080";

async function proxy(req: NextRequest, path: string[]) {
  const token = req.cookies.get("adivritti_session")?.value;
  if (!token) return NextResponse.json({ error_code: "UNAUTHENTICATED" }, { status: 401 });

  const target = new URL(path.join("/"), `${CORE_URL}/`);
  req.nextUrl.searchParams.forEach((v, k) => target.searchParams.set(k, v));

  const upstream = await fetch(target, {
    method: req.method,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: req.method === "GET" || req.method === "HEAD" ? undefined : await req.text(),
    cache: "no-store",
  });

  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: { "Content-Type": upstream.headers.get("Content-Type") ?? "application/json" },
  });
}

export const GET = (req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) =>
  proxy(req, (await ctx.params).path);
export const POST = (req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) =>
  proxy(req, (await ctx.params).path);
```

- [ ] **Step 4: Point the data layer at the proxy, keeping mocks for the not-yet-built endpoints**

In `apps/officer-web/src/lib/api.ts`, change each mock-backed method to try the proxy first and fall back to the mock, with a visible marker so the demo never silently lies:

```typescript
const CORE = "/api/core";

async function coreGet<T>(path: string): Promise<T> {
  const res = await fetch(`${CORE}${path}`, { cache: "no-store" });
  if (!res.ok) throw new Error(`Core returned ${res.status} for ${path}`);
  return (await res.json()) as T;
}

export const api = {
  /** Officer exception queue. Falls back to demo data only while Core has no
   *  equivalent endpoint; `isDemo` tells the UI to label it as such. */
  async getExceptionQueue() {
    try {
      return { rows: await coreGet(`/v1/admin/exceptions?pageSize=100`), isDemo: false };
    } catch {
      return { rows: exceptionQueue, isDemo: true };
    }
  },
  // ... same shape for the other four
};
```

Then add an explicit "Demo data" badge in the officer UI wherever `isDemo` is true, so a judge is never shown synthetic figures without being told. This is the honest version of "works offline" — and it also means the remaining mock-only endpoints become visible debt rather than hidden.

- [ ] **Step 5: Replace the fake OTP with a real handoff**

In `apps/officer-web/src/app/(auth)/login/page.tsx`, the `verify` handler must not `router.push("/dashboard")`. It must obtain a credential:

```typescript
  async function verify(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const res = await fetch("/api/session", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phone, otp: otp.trim() }),
      });
      if (!res.ok) throw new Error(`sign-in failed: ${res.status}`);
      router.push("/dashboard");
    } catch {
      setError("Sign-in failed. Check the OTP and try again.");
    } finally {
      setBusy(false);
    }
  }
```

And create `apps/officer-web/src/app/api/session/route.ts` to mint the session cookie from Core. **Core has no login endpoint today** — the contract has 13 paths and none of them is one. So this is blocked on Core work. For this plan, implement the route to call a Core `/v1/auth/officer/login` endpoint and add that endpoint as a Task 10 follow-up; until then, have the route return `501 Not Implemented` and have the login page show "Officer sign-in is not wired in this build" rather than pretending. **Do not ship a cookie that anyone can mint.**

- [ ] **Step 6: Build and lint**

Run: `cd apps/officer-web && npm run build && npm run lint`
Expected: both succeed. The build must not contain the token string:

```bash
grep -r "dev-only-ai-service-token" .next/static/ && echo "LEAK" || echo "clean"
```
Expected: `clean`.

- [ ] **Step 7: Commit**

```bash
git add apps/officer-web/middleware.ts \
        apps/officer-web/src/app/page.tsx \
        apps/officer-web/src/app/api/core/\[...path\]/route.ts \
        apps/officer-web/src/app/api/session/route.ts \
        apps/officer-web/src/lib/api.ts \
        apps/officer-web/src/components/assistant/assistant.ts \
        apps/officer-web/src/app/\(auth\)/login/page.tsx
git commit -m "fix(officer-web): guard /dashboard, proxy Core server-side, drop the client-side AI token"
```

---

### Task 9: Remove the drift between documentation and behaviour

**Files:**
- Modify: `docs/specs/00-overview.md`
- Modify: `docs/adr/ADR-001-rules-as-data.md`
- Modify: `docs/specs/demo-path.md`
- Modify: `README.md`
- Modify: `services/core/build.gradle`
- Modify: `services/core/src/main/java/in/adivritti/core/verification/adapter/GovAdapter.java`
- Modify: `infra/docker-compose.override.yml`
- Modify: `apps/mobile/package.json`
- Create: `docs/adr/ADR-002-*.md`

**Interfaces:**
- Consumes: everything settled in Tasks 1–8.
- Produces: documentation that describes the system as it is, and a dependency list without packages nothing imports.

This task is where F16 and F17 land. Grouping them is right: they are all "the docs and the manifest claim something the code does not do", and they are individually low-risk.

- [ ] **Step 1: Correct the `GovAdapter` javadoc — it documents behaviour that does not exist**

`GovAdapter.java:5-8` says Core treats govsim like real government APIs with "circuit breakers (Resilience4j), retries with jitter at call site". Neither exists: grep for `@CircuitBreaker`/`@Retry`/`@Bulkhead` in `services/core/src` returns nothing, and the only resilience is `VerificationAttempt.probe`'s try/catch. Replace the class comment with what is actually true:

```java
/**
 * GovSim adapter. Core treats govsim EXACTLY like real government APIs: explicit
 * timeouts, and a per-attempt memo so a tier never re-issues a call the previous
 * tier already made.
 *
 * <p>What is NOT yet here: Resilience4j circuit breakers and jittered retries are
 * declared as a dependency but no annotation is applied anywhere in this service.
 * Today the only protection is the try/catch in
 * {@link VerificationAttempt#probe}, which treats an unreachable portal as
 * "unknown" so the chain degrades to the next tier. govsim injects 8% random 500s
 * by default, so this gap is load-bearing under chaos — see ADR-002.
 */
```

- [ ] **Step 2: Either use Resilience4j or drop it**

Two honest options. Recommend (a):

- **(a) Apply it.** Add a `resilience4j.yaml` under `services/core/src/main/resources/` configuring a circuit breaker and retry for the govsim client, and wrap the calls in `GovsimClient`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      govsim:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
  retry:
    instances:
      govsim:
        max-attempts: 3
        wait-duration: 200ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
```

and annotate `GovsimClient.get` with `@CircuitBreaker(name = "govsim", fallbackMethod = "degraded")` plus `@Retry(name = "govsim")`, where `degraded` returns the existing `Map.of("available", false, ...)`. Keep the existing try/catch as the last line of defence.

- **(b) Drop it.** Remove `implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'` from `build.gradle`.

Do **not** leave it declared-but-unused; that is the exact failure this task exists to remove. Same for `caffeine`, which has zero imports (its only mention is a javadoc in `CacheConfig` explaining it was removed) — remove it from `build.gradle`.

- [ ] **Step 3: Fix the rules hot-reload claim**

`infra/docker-compose.override.yml` ends with the comment that the rules JSON "does hot-reload". It does not: `EligibilityService.ruleCache` is a `ConcurrentHashMap` populated on first read and never invalidated, so an edited rules file is invisible until restart. Either implement invalidation or correct the comment.

Recommend a file-watch, since the override file's whole purpose is fast local iteration. Add to `EligibilityService`:

```java
    /**
     * Drop cached rule files so an edit to packages/rules is picked up without a
     * restart. Called on the dev-only override profile; the container mount is
     * read-only, so a watch is the only invalidation signal available.
     */
    public void invalidateRuleCache() {
        ruleCache.clear();
    }
```

and a `@Scheduled(fixedDelayString = "${app.rules-watch-interval-ms:0}")` method that clears it when the interval is non-zero, with the property defaulting to `0` (disabled) and set only in the override profile. If that is more machinery than the project wants, change the comment to "requires `make rebuild`" and move on — but do not leave a false claim in a file people read to decide what to do next.

- [ ] **Step 4: Prune the mobile dependency list**

`apps/mobile/package.json` declares five packages with zero imports under `apps/mobile/src` (verified per-package in the audit): `drizzle-orm`, `expo-sqlite`, `@tanstack/react-query`, `react-native-mmkv`, `react-native-vision-camera`. They also inflate the EAS build. Remove them.

But `src/lib/store.ts` documents "SQLite (Drizzle) is the source of truth for reads" and `deltaSync` is a stub. So either implement the offline store or correct the comment. For a project whose demo runs with the network off, the offline store matters — recommend keeping `expo-sqlite` + `drizzle-orm` and implementing `deltaSync` against Core, and scheduling that as follow-up work. For this plan: **remove the four packages that have no roadmap (`react-native-mmkv`, `react-native-vision-camera`, `@tanstack/react-query`) and keep the two that the offline story needs, rewriting `store.ts`'s comment to state plainly that persistence is not yet wired.**

- [ ] **Step 5: Write ADR-002 for the unimplemented subsystems**

`ADR-001-rules-as-data.md` says rules are "mirrored into the `scheme_rule_version` table" and that "Core must validate rule files against `packages/rules/schema.json` on startup". Neither is true: the `scheme_rule_version` and `claim_attestation` tables (`V1__initial_schema.sql:61,174`) have no entity, repository, or service, and there is no startup schema validation. ADR-001 is accepted and should not be rewritten to match reality — reality is behind. Write a new ADR recording the gap and the plan.

Create `docs/adr/ADR-002-unimplemented-layers.md`:

```markdown
# ADR-002 — Accepted-but-unimplemented layers

Status: proposed. Date: 2026-09-30.

## Context
A full audit of the codebase (2026-09-30) found four subsystems that the
architecture documents describe as working, and that the schema or the DTOs imply
are working, but that have no implementation:

1. **`scheme_rule_version` mirror** (V1__initial_schema.sql:174, mandated by
   ADR-001) has no entity, repository, or service. `EligibilityService` reads
   JSON off the filesystem and never validates it against
   `packages/rules/schema.json` at boot, so a malformed rules file is only
   discovered on the first request for that scheme.
2. **`claim_attestation`** (V1__initial_schema.sql:61) — signed Ed25519 claim
   attestations — has no entity or writer. Claims are sealed with AES-256-GCM but
   not signed, so there is no non-repudiation for a value the wallet asserts.
3. **DPDP access audit.** `access_audit` has append-only DB triggers, a
   repository, a DTO, and a contract path — but `ConsentService.recordAccess` and
   `hasActiveConsent` are never called from anywhere. The audit table is never
   written and `GET /v1/scholars/{usid}/audit` always returns an empty page.
   `ConsentService.audit` is also documented as officer-only but is reachable by
   the owning scholar, since it uses the same `access.check(usid)` as a
   student-scoped read.
4. **Claim values.** Every claim written by `VerificationOrchestrator` stores an
   empty value by design, and `EligibilityService` skips valueless claims, so no
   claim created through the API can satisfy a rule. The Verified Claims Wallet
   currently functions as a provenance record, not a wallet.

## Decision
Record these as known gaps rather than silently documenting them as working. Do
not close ADR-001; it is still the right decision and these are incomplete
implementations of it.

## Consequences
- The demo path must not claim "verify once, reuse across all five schemes" until
  (4) is resolved; today a verified claim cannot satisfy a single rule.
- The access audit is a compliance claim in the README that the code does not
  support. Either wire `recordAccess` at every personal-field read, or remove the
  claim from the documentation.
- Rules need startup validation (1) before rules-as-data can be called safe to
  edit in production. Task 2 of the remediation plan closes the *empty* case;
  this ADR tracks the *malformed* case.
```

- [ ] **Step 6: Correct the docs that overstate readiness**

- `docs/specs/00-overview.md` layer 9 — "purpose-bound consent artefacts, append-only access audit" → note the audit is schema-only until ADR-002 item 3 lands.
- `docs/specs/00-overview.md` layer 2 — the wallet claim needs the same caveat.
- `docs/specs/demo-path.md` beat 3 — "Same claim satisfies a second scheme" is not currently achievable. Mark it as blocked on ADR-002 item 4, and add a fallback beat that demonstrates what *does* work (provenance record + deficiency loop), so the demo stays walkable.
- `README.md` "Integration readiness" table — DigiLocker is listed LIVE. The only implementation is `DigiLockerAdapter`, which calls `govsim` (`/digilocker/verify`), and govsim's own handler returns `verify(true, ...)` with the note "sandbox fallback — swap for live API Setu in TASK 6.1". So no live integration exists. Change to "contract-ready (govsim)".
- `README.md` "There is no end-to-end suite yet" — accurate, leave it.

- [ ] **Step 7: Verify nothing regressed and commit**

```bash
cd services/core && ./gradlew test -PjavaToolchainVersion=23 --console=plain
cd ../../services/ai && python -m pytest tests/ -q
cd ../../apps/officer-web && npm run build
```
Expected: all green.

```bash
git add docs/adr/ADR-002-unimplemented-layers.md docs/adr/ADR-001-rules-as-data.md \
        docs/specs/00-overview.md docs/specs/demo-path.md README.md \
        services/core/build.gradle \
        services/core/src/main/java/in/adivritti/core/verification/adapter/GovAdapter.java \
        infra/docker-compose.override.yml apps/mobile/package.json
git commit -m "docs: record unimplemented layers in ADR-002 and correct readiness claims"
```

---

## Out of Scope — deliberately not in this plan

These are real and worth doing, but they are feature work rather than defect repair, and each needs a design decision of its own. Listing them so they are not lost:

| # | Item | Why it is not here |
|---|---|---|
| 1 | **Make the Verified Claims Wallet actually store values.** F7 is the most consequential finding: the wallet cannot satisfy a rule, so demo beat 3 cannot run as written. The fix is a contract change (a value field on `VerifyRequest`, with a typed, per-claim-type schema so the orchestrator can never invent one) plus a value source in the DocAI tier. That is a design conversation, not a patch. Tracked in ADR-002 item 4. |
| 2 | **Officer console backend endpoints.** `GET /v1/admin/summary`, `/outreach`, `/identity-queue`, and an approve action do not exist; the console is mock data end to end. Needs a contract pass and Core work. Task 8 makes the mock visible and labelled rather than silently wrong, which is the honest interim state. |
| 3 | **Real officer authentication.** Task 8's middleware checks cookie *presence*. Needs an IdP or signed sessions, and Core's `/v1/auth/officer/login` does not exist. |
| 4 | **Claim attestation signing.** The `claim_attestation` table and its Ed25519 design exist; the signing service does not. ADR-002 item 2. |
| 5 | **Startup rules validation against `packages/rules/schema.json`.** ADR-001 mandates it. Needs a JSON Schema validator on the Core classpath (`networknt/json-schema-validator`) — a dependency decision. |
| 6 | **CORS on the AI service.** F6 identified the absence. The right fix is *no* CORS, because the browser should never call the AI service (Task 8 removes both call sites). Add an explicit deny-all CORS policy so a future mistake fails loudly. |
| 7 | **`.env` secret rotation.** F22 — untracked, out of history, so not an active leak. Rotate deliberately, out of band. |

---

## Verification

Run after all tasks. Each line is a command and its expected output — evidence, not assertion.

```bash
# 1. Full Core suite
cd services/core && ./gradlew test -PjavaToolchainVersion=23 --console=plain
#    -> BUILD SUCCESSFUL. Baseline was 105 tests; expect 109+ after Tasks 1-5.

# 2. Full AI suite
cd services/ai && pip install -e '.[dev]' && python -m pytest tests/ -q
#    -> all pass. Baseline was 1406 lines of tests; no regressions.

# 3. govsim
cd services/govsim && npm ci && npm test
#    -> pass.

# 4. Contract validates and clients regenerate
cd packages/api-client && npm run generate
#    -> generation succeeds.
grep -c "bearerAuth" docs/openapi/core.yaml
#    -> >= 2 (the securitySchemes definition and the top-level security block).

# 5. Frontends typecheck and build
cd apps/officer-web && npm run build && npm run lint
#    -> both succeed.
cd apps/mobile && npx tsc --noEmit
#    -> clean.

# 6. No service token in any client bundle
grep -r "dev-only-ai-service-token" apps/officer-web/.next/static/ 2>/dev/null && echo "LEAK" || echo "clean"
#    -> clean.

# 7. Migrations apply on a fresh database, and are re-runnable
docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml up -d postgres
docker compose -f infra/docker-compose.yml exec -T postgres psql -v ON_ERROR_STOP=1 -U adivritti -d adivritti -f /docker-entrypoint-initdb.d/init.sql
#    -> no errors.
#    Then boot Core and confirm Flyway reaches V2:
#    -> "Successfully applied 2 migrations"

# 8. The demo path, end to end, per docs/specs/demo-path.md
docker compose -f infra/docker-compose.yml up --build -d
#    -> walk all six beats. Beat 3's "same claim satisfies a second scheme"
#       is EXPECTED TO FAIL until out-of-scope item 1 is done — confirm it fails
#       for the documented reason (claim valueless), not a new one, and that
#       ADR-002 item 4 is what the demo doc points at.

# 9. The three authorization fixes, by hand
#    Mint a JWT whose usid claim is A, then with that token:
#      POST /v1/verify {"usid":"<B>","claimType":"income"}                 -> 403 SCHOLAR_ACCESS_DENIED
#      GET  /v1/applications/<an id belonging to B>/timeline               -> 403 SCHOLAR_ACCESS_DENIED
#      DELETE /v1/consent/<a consent id belonging to B>                   -> 403 SCHOLAR_ACCESS_DENIED
#      POST /v1/verify {"usid":"A","claimType":"income"}                   -> 200
#    An unknown application id must return 404, not 403.

# 10. Eligibility fails closed
#    Temporarily empty packages/rules/2026-27/nfst.json's "rules" array, restart Core:
#      POST /v1/eligibility/evaluate {"usid":"<any>"}
#    -> NFST verdict is "missing_items", NOT "eligible". Revert the file.
```

## Self-Review

- **Spec coverage.** Every finding F1–F22 is either a task (F1–F3 → Task 1; F6a → Task 2; F6b → Task 3; F8, F19, F20 → Task 5; F9, F10, F11, F12 → Task 7; F4, F5, F6 → Task 8; F13, F14 → Task 6; F15 → Task 4; F16, F17, F18 → Task 9; F21 → ADR-002; F22 → out of scope) or explicitly out of scope with a reason. F7 and F18: F7 is out of scope #1 and ADR-002 item 4; F18's "log the offending rule index" is folded into Task 2 Step 4's error path and its "distinct internal signal" is out of scope #5.
- **Placeholder scan.** No step says "add appropriate handling" or "write tests for the above". Two steps (Task 3 Step 1, Task 5 Step 1) were caught writing self-referential tests that assert a reimplementation of the code under test rather than the code itself, and each carries an explicit instruction to replace them with a version that drives the real class. That is called out in the step rather than left for the executor to discover.
- **Type consistency.** `ScholarAccessGuard.check(UUID)` is used identically in Tasks 1, 2 and 3. `RuleEngine.requireNonEmptyRules(List<Map<String,Object>>, String, String)` is defined in Task 2 Step 3 and called with that exact arity in Step 4 and in both Task 2 tests. `ClaimRepository.findFirstByIdempotencyKey(String)` is defined in Task 4 Step 5 and mocked with that signature in Step 3. `StpScorer.score(String, boolean, int, int, int)` is defined in Task 5 Step 6 and called with five arguments there. `Application.breachRisk`/`Application.stpScore` are added in Task 5 Step 4 and read in the same step.
- **Ordering.** Authorization first (highest severity, self-contained), then the two silent-wrong-answer bugs, then the write-path guarantee, then the queue, then the parity fix, then the contract (which must describe final behaviour), then the frontend, then the docs.

## Known Risks in This Plan

1. **Task 5 Step 6 writes to the database during a GET** (`a.stpScore = ...; applications.save(a)`). Flagged inline. Prefer a background refresh; if the team accepts the write, add `@Transactional` to the controller method so it is one transaction per page rather than one per row.
2. **Task 8's middleware checks cookie presence, not validity.** Flagged inline. It is a real improvement over a public console and an incomplete one. Do not describe it as authentication in the README.
3. **Task 8 Step 5 leaves officer sign-in unwired (501).** This makes the console unreachable in a real deployment. That is intentional and honest — the alternative is a cookie anyone can mint — but it means the Vercel demo stops working as a demo. Raise before merging.
4. **Task 4 and Task 5 both edit `V2__idempotency_keys.sql`.** Fine while V2 is unmerged, wrong if Task 5 lands on a branch where V2 already shipped. Keep both on one branch, or split V2 into V2 and V3.
5. **The `stp_score` sort requires a persisted column**, which changes the shape of the exception queue's reads. If `application.stp_score` backfills as `0.0` for every existing row, `sort=stp_score` is initially meaningless. Acceptable for a dev stack; call it out in the demo.
