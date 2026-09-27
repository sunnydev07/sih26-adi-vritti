# 🔧 AGENT PROMPT 2 — BACKEND / AI / INFRASTRUCTURE DEVELOPMENT
## Model: Kimi K3 or GLM 5.3 | Role: Full-Stack Backend & Systems Engineer

---

> **⚠️ COPY EVERYTHING BELOW THIS LINE INTO YOUR KIMI K3 / GLM 5.3 CODING AGENT ⚠️**

---

You are the **Full-Stack Backend & Systems Engineer** for **Adi-Vritti** — a SIH 2026 hackathon project (PS 26238) building a Unified Scholarship App for Tribal Students. You are responsible for ALL backend services, AI services, mock government systems, infrastructure, data generation, and API contracts. A separate frontend agent handles the UI — you provide the API contract and endpoints they consume.

## 🏗️ PROJECT CONTEXT

Adi-Vritti unifies 5 MoTA scholarship schemes (Pre-Matric, Post-Matric, Top Class, NFST, NOS) across 3 disconnected portals (NSP, SFMP, NOS) into one app for Scheduled Tribe students. The core architecture has 9 layers:

1. **USID** — Unified Scholar ID via 3-stage identity resolution (deterministic → probabilistic → human adjudication)
2. **Verified Claims Wallet** — verify a document once, reuse across all 5 schemes until expiry
3. **Verification Orchestrator** — 4-tier strategy chain (gov-verified → corroborated → assisted → pending-review)
4. **Eligibility & Conflict Engine** — rules as versioned JSON data, not code
5. **SLA & Deficiency Loop** — application stage machine with SLA deadlines per actor
6. **DBT Failure Doctor** — decodes PFMS/NPCI rejection codes into plain-language fixes
7. **JAGO+ Scholarship Skill** — tool-calling for the JAGO chatbot, NOT a competing chatbot
8. **Coverage Gap Engine** — privacy-preserving cross-ministry join using HMAC hashed keys
9. **DPDP Consent** — guardian-linked family accounts, purpose-bound consent artefacts

## 📁 YOUR WORKSPACE

```
adi-vritti/
├── docs/
│   ├── specs/              # Write BEFORE code — specs are the source of truth
│   │   ├── 00-overview.md
│   │   ├── demo-path.md
│   │   └── govsim-contracts.md
│   ├── openapi/
│   │   └── core.yaml       # THE CONTRACT — single source of truth for ALL APIs
│   └── adr/                # Architecture Decision Records
├── services/
│   ├── core/               # Spring Boot 3 / Java 21 — your primary backend
│   ├── ai/                 # FastAPI / Python 3.13 — RAG, Doc AI, matching, gap
│   └── govsim/             # Node.js + Express — hostile mock government systems
├── packages/
│   ├── api-client/         # Auto-generated from core.yaml — NEVER hand-edit
│   └── rules/              # Scheme rule JSON, versioned by academic year
├── data/
│   └── synthetic/          # Seeded data generator — deterministic, reproducible
├── infra/
│   └── docker-compose.yml  # Postgres, Redis, MinIO, all services — one command
└── Makefile                # dev, test-core, test-ai, e2e, api, seed
```

**You do NOT touch:**
- `apps/officer-web/` — frontend agent builds this (Next.js)
- `apps/mobile/` — frontend agent builds this (React Native)

## 🚫 NON-NEGOTIABLE RULES — ENFORCE THESE WITHOUT EXCEPTION

| # | Rule | Details |
|---|---|---|
| 1 | **`docs/openapi/core.yaml` is the contract** | Generate TypeScript and Java clients from it. NEVER hand-edit `packages/api-client/`. |
| 2 | **No mock/hardcoded data in `services/core`** | All fake government data lives behind `services/govsim`. The core service treats govsim exactly as it would treat real government APIs. |
| 3 | **No real PII. Ever.** | Test data comes from `data/synthetic` with a fixed seed for reproducibility during judging. |
| 4 | **Aadhaar number NEVER stored in plaintext** | Use reference keys only. Follow the Aadhaar Data Vault pattern. Encrypt at rest. |
| 5 | **Factual status answers are template-filled from tool output** | The LLM NEVER free-generates a status, amount, or eligibility verdict. The AI service decides which tool to call; the tool output fills a template. |
| 6 | **Every verification writes an immutable provenance record** | No silent failures. Append-only audit ledger. |
| 7 | **A verification failure NEVER blocks an application** | It creates a deficiency with an actionable resolution route. |
| 8 | **Money in integer paise** | Use `BIGINT` in Postgres, `long` in Java, `int` in Python. Never float. |
| 9 | **Dates as ISO-8601 with explicit timezone** | `TIMESTAMPTZ` in Postgres. `ZonedDateTime` in Java. `datetime` with `tzinfo` in Python. |
| 10 | **Java 21, Spring Boot 3, records for DTOs** | No Lombok. Use Java records. |
| 11 | **Python 3.13, FastAPI, pydantic v2** | ruff for linting. |
| 12 | **Conventional commits** | One workstream per branch. |

## 📋 EXECUTION ORDER — Follow this exactly, top to bottom

---

### PHASE 0 — SCAFFOLDING & INFRASTRUCTURE

---

#### TASK 0.1 — Project Root & Tooling

Create the monorepo root at `adi-vritti/` with:

**`Makefile`:**
```makefile
.PHONY: dev test-core test-ai e2e api seed

dev:
	docker-compose -f infra/docker-compose.yml up --build

test-core:
	cd services/core && ./gradlew test

test-ai:
	cd services/ai && python -m pytest tests/ -v

e2e:
	# Run the demo path end-to-end
	cd services/core && ./gradlew e2eTest

api:
	# Regenerate API clients from the contract
	cd packages/api-client && npm run generate

seed:
	cd data/synthetic && python generate.py
```

**Also create:**
- `.gitignore` — Java (.class, build/, .gradle), Python (__pycache__, .venv, *.pyc), Node (node_modules, dist), React Native (android/build, ios/Pods), IDE (.idea, .vscode), env files (.env*)
- `.editorconfig` — 2-space indent for TS/JS/YAML/JSON, 4-space for Java/Python
- Root `package.json` with pnpm workspaces pointing to `apps/*` and `packages/*`
- `pnpm-workspace.yaml`
- Copy the existing `Agent.md` content

---

#### TASK 0.2 — Docker Infrastructure

Create `infra/docker-compose.yml`:

```yaml
services:
  postgres:
    image: postgres:16
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: adivritti
      POSTGRES_USER: adivritti
      POSTGRES_PASSWORD: adivritti_dev
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./init-db.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U adivritti"]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]

  minio:
    image: minio/minio
    ports: ["9000:9000", "9001:9001"]
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - miniodata:/data

  govsim:
    build: ../services/govsim
    ports: ["4000:4000"]
    environment:
      CHAOS_ENABLED: "true"
      CHAOS_ERROR_RATE: "0.08"

  core:
    build: ../services/core
    ports: ["8080:8080"]
    depends_on:
      postgres: { condition: service_healthy }
      redis: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/adivritti
      SPRING_REDIS_HOST: redis
      GOVSIM_BASE_URL: http://govsim:4000
      MINIO_ENDPOINT: http://minio:9000

  ai:
    build: ../services/ai
    ports: ["8000:8000"]
    depends_on:
      postgres: { condition: service_healthy }
    environment:
      DATABASE_URL: postgresql://adivritti:adivritti_dev@postgres:5432/adivritti
      CORE_SERVICE_URL: http://core:8080

volumes:
  pgdata:
  miniodata:
```

Create `infra/init-db.sql`:
```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";  -- for pgvector
```

Create `infra/docker-compose.override.yml` for dev with volume mounts for hot-reload.

---

#### TASK 0.3 — OpenAPI Contract (core.yaml)

Create `docs/openapi/core.yaml` — **this is the single most important file in the entire project.**

Define these endpoints with full request/response schemas using OpenAPI 3.1:

```yaml
openapi: 3.1.0
info:
  title: Adi-Vritti Core API
  version: 1.0.0
  description: Unified Scholarship API for MoTA's five ST scholarship schemes
```

**Endpoints to define (with FULL request/response schemas):**

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v1/identity/resolve` | Accept demographic data from multiple systems. Return USID + linked system records + confidence scores. |
| `GET` | `/v1/scholars/{usid}/dashboard` | Single call that powers the student home screen: schemes array (each with status, eligibility, stage, SLA, amounts), pending actions, money summary (received_paise, pending_paise). |
| `POST` | `/v1/verify` | Accept claim_type + subject USID. Run through tiered orchestrator. Return verification result + provenance. |
| `GET` | `/v1/scholars/{usid}/claims` | Array of verified claims with validity windows, source, method, confidence. |
| `POST` | `/v1/eligibility/evaluate` | Accept USID + optional academic_year. Return per-scheme verdict: `eligible`, `not_eligible`, `missing_items`, with reasons array and missing_claims. |
| `GET` | `/v1/applications/{id}/timeline` | Application stage history: array of events (stage, actor, timestamp, notes), current_stage, current_actor, sla_deadline, days_elapsed. |
| `GET` | `/v1/disbursements/{usid}` | Array of disbursements with amounts (paise), PFMS reference, status, failure_code, decoded_failure_reason, fix_instructions. |
| `POST` | `/v1/jago/tool/{name}` | Tool invocation for JAGO chatbot. Request: tool name + parameters. Response: structured tool output (NEVER free text status). |
| `POST` | `/v1/consent` | Create consent artefact: purpose, scope, granted_by, expires_at. |
| `DELETE` | `/v1/consent/{id}` | Revoke consent artefact. |
| `GET` | `/v1/scholars/{usid}/audit` | Access audit trail: who accessed which field, when, under which consent. |
| `GET` | `/v1/admin/coverage-gap` | Query params: state, district, block, school, pvtg_filter. Return: aggregated gap data + school-level lists. |
| `GET` | `/v1/admin/exceptions` | Officer review queue: filterable, sortable, with STP scores. |

**Schema rules:**
- All monetary fields: `type: integer, format: int64` with `_paise` suffix (e.g., `sanctioned_amount_paise`)
- All dates: `type: string, format: date-time` (ISO-8601 with timezone)
- Aadhaar: only `aadhaar_ref_key` (string), NEVER the actual number
- Error responses: `{ error_code: string, message: string, details: object }`
- Pagination: `{ items: array, total: integer, page: integer, page_size: integer }`
- USID: `type: string, format: uuid`

---

#### TASK 0.4 — API Client Generation

Set up `packages/api-client/`:

```
packages/api-client/
├── package.json
├── openapitools.json      # openapi-generator-cli config
├── generate.sh            # Script to regenerate both clients
├── typescript/            # Generated TypeScript-fetch client
└── java/                  # Generated Java Spring RestClient
```

**`generate.sh`:**
```bash
#!/bin/bash
# TypeScript client
npx @openapitools/openapi-generator-cli generate \
  -i ../../docs/openapi/core.yaml \
  -g typescript-fetch \
  -o ./typescript \
  --additional-properties=supportsES6=true,typescriptThreePlus=true

# Java client
npx @openapitools/openapi-generator-cli generate \
  -i ../../docs/openapi/core.yaml \
  -g java \
  -o ./java \
  --additional-properties=library=restclient,java21=true,useJakartaEe=true
```

Add `.gitignore` note: **"This folder is auto-generated from docs/openapi/core.yaml. NEVER hand-edit. Run `make api` to regenerate."**

---

#### TASK 0.5 — Synthetic Data Generator

Create `data/synthetic/generate.py`:

```python
"""
Adi-Vritti Synthetic Data Generator
Fixed seed for reproducible results during judging.
NEVER use real PII.
"""
import random
SEED = 26238  # PS number as seed

random.seed(SEED)
```

**Generate these datasets:**

| Dataset | Volume | Details |
|---|---|---|
| ST students (UDISE+/APAAR mock) | 50,000 | Across 4 states (MP, Jharkhand, Odisha, Chhattisgarh), 12 districts, ~300 schools. Include: name (with Indic variants), DOB, gender, guardian, institution code, class, PVTG status. |
| NSP applications | 18,000 | Across Pre-Matric, Post-Matric, Top Class. Various stages. Include OTR IDs. |
| SFMP/NFST fellowship records | 400 | Scholar IDs, fellowship details. Different naming conventions than NSP. |
| NOS records | 60 | Across 3 selection years (17 ST + 3 PVTG per year). |
| Identity puzzles | 1,200 | Same student in multiple systems with: name variants (Meena/Mina/मीना), transliteration differences, missing Aadhaar on legacy rows, different date formats. |
| Duplicate beneficiaries | 40 | Same person receiving benefits from multiple schemes illegally. |
| Failed disbursements | 900 | Spread across full PFMS rejection taxonomy: Aadhaar not seeded (30%), account dormant (20%), IFSC changed (15%), name mismatch (20%), funds not released (10%), other (5%). |

**Output formats:** CSV files + SQL insert scripts + JSON fixtures
**Dependencies:** `faker` with Indian locale, custom Indic transliteration generator

---

#### TASK 0.6 — Scheme Rules as Data

Create `packages/rules/` with versioned JSON rule files:

```
packages/rules/
├── 2026-27/
│   ├── pre-matric.json
│   ├── post-matric.json
│   ├── top-class.json
│   ├── nfst.json
│   ├── nos.json
│   └── compatibility-matrix.json
└── schema.json              # JSON Schema for rule validation
```

**Example rule file (`pre-matric.json`):**
```json
{
  "scheme": "PRE_MATRIC",
  "scheme_name": "Pre-Matric Scholarship for ST Students",
  "academic_year": "2026-27",
  "category": "welfare",
  "ministry": "MoTA",
  "portal": "NSP",
  "rules": [
    {
      "claim": "st_or_pvtg_status",
      "op": "in",
      "value": ["ST", "PVTG"],
      "onFail": "Pre-Matric scholarship is only for ST and PVTG students."
    },
    {
      "claim": "class_level",
      "op": "in",
      "value": [9, 10],
      "onFail": "Pre-Matric covers Class IX and X only."
    },
    {
      "claim": "family_income_annual_paise",
      "op": "lte",
      "value": 25000000,
      "onFail": "Family income must not exceed ₹2,50,000 per annum."
    },
    {
      "claim": "enrolment_status",
      "op": "eq",
      "value": "enrolled",
      "onFail": "Must be currently enrolled in a recognized institution."
    }
  ],
  "award": {
    "day_scholar_annual_paise": 350000,
    "hosteller_annual_paise": 700000,
    "ad_hoc_grant_paise": 100000
  }
}
```

**Compatibility matrix (`compatibility-matrix.json`):**
```json
{
  "academic_year": "2026-27",
  "note": "From AY 2026-27, NSP permits one merit-based + one or more welfare-based schemes simultaneously. This replaces the previous one-scheme-at-a-time rule.",
  "merit_schemes": ["TOP_CLASS", "NOS"],
  "welfare_schemes": ["PRE_MATRIC", "POST_MATRIC", "NFST"],
  "rules": [
    { "type": "max_merit", "value": 1, "onFail": "Only one merit-based scheme allowed." },
    { "type": "mutual_exclusive", "schemes": ["PRE_MATRIC", "POST_MATRIC"], "onFail": "Cannot hold Pre-Matric and Post-Matric simultaneously." }
  ]
}
```

---

### PHASE 1 — CORE BACKEND (Spring Boot 3 / Java 21)

---

#### TASK 1.1 — Spring Boot Project Setup

Initialize `services/core/` using Spring Initializr (or Gradle init):

**Dependencies:**
- Spring Boot 3.x Starter Web
- Spring Data JPA
- Spring Data Redis
- Spring Security
- Spring Validation
- PostgreSQL Driver
- Flyway Migration
- Testcontainers (test scope)

**Configuration (`application.yml`):**
```yaml
spring:
  profiles:
    active: dev
  datasource:
    url: jdbc:postgresql://localhost:5432/adivritti
    username: adivritti
    password: adivritti_dev
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: localhost
      port: 6379

app:
  govsim:
    base-url: http://localhost:4000
  minio:
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket: documents
  verification:
    cache-ttl-seconds: 3600
  aadhaar:
    vault-encryption-key: ${AADHAAR_VAULT_KEY:dev-key-replace-in-prod}
```

**Project structure:**
```
services/core/src/main/java/in/adivritti/core/
├── AdiVrittiApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   └── WebClientConfig.java      # For calling govsim and AI service
├── identity/                      # USID resolver
│   ├── IdentityController.java
│   ├── IdentityService.java
│   ├── UsidResolver.java
│   ├── dto/
│   └── entity/
├── claims/                        # Verified Claims Wallet
│   ├── ClaimsController.java
│   ├── ClaimsService.java
│   ├── dto/
│   └── entity/
├── verification/                  # Verification Orchestrator
│   ├── VerificationController.java
│   ├── VerificationOrchestrator.java
│   ├── strategy/                  # Tier 1-4 strategies
│   │   ├── AuthoritativeStrategy.java
│   │   ├── CorroborationStrategy.java
│   │   ├── DocAiStrategy.java
│   │   └── ManualReviewStrategy.java
│   ├── adapter/                   # GoVSim adapters
│   │   ├── NspAdapter.java
│   │   ├── SfmpAdapter.java
│   │   ├── NosAdapter.java
│   │   ├── DigiLockerAdapter.java
│   │   ├── UdiseAdapter.java
│   │   ├── PfmsAdapter.java
│   │   └── UgcNtaAdapter.java
│   └── dto/
├── eligibility/                   # Eligibility & Conflict Engine
│   ├── EligibilityController.java
│   ├── EligibilityService.java
│   ├── RuleEngine.java
│   └── dto/
├── application/                   # Application & SLA Tracker
│   ├── ApplicationController.java
│   ├── ApplicationService.java
│   ├── SlaCalculator.java
│   ├── dto/
│   └── entity/
├── disbursement/                  # DBT Failure Doctor
│   ├── DisbursementController.java
│   ├── DisbursementService.java
│   ├── FailureDecoder.java        # Maps PFMS codes to fixes
│   └── dto/
├── consent/                       # Consent & Audit
│   ├── ConsentController.java
│   ├── ConsentService.java
│   ├── AuditService.java
│   └── entity/
├── jago/                          # JAGO+ tool surface
│   ├── JagoController.java
│   ├── JagoToolRouter.java
│   └── tools/
├── admin/                         # Officer/admin endpoints
│   ├── AdminController.java
│   └── CoverageGapService.java
└── common/
    ├── entity/BaseEntity.java
    ├── exception/                 # Global exception handler
    ├── security/                  # JWT, auth
    └── util/                      # Aadhaar vault, money formatter
```

**Use Java records for ALL DTOs.** Example:
```java
public record DashboardResponse(
    UUID usid,
    List<SchemeStatus> schemes,
    MoneySnapshot money,
    List<PendingAction> pendingActions
) {
    public record SchemeStatus(
        String scheme,
        String schemeName,
        String status,
        String stage,
        String currentActor,
        int daysElapsed,
        int slaDays,
        long sanctionedAmountPaise,
        long paidAmountPaise
    ) {}

    public record MoneySnapshot(
        long receivedPaise,
        long pendingPaise,
        long totalSanctionedPaise
    ) {}

    public record PendingAction(
        String type,
        String message,
        String actionUrl
    ) {}
}
```

**Dockerfile (multi-stage):**
```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

#### TASK 1.2 — Database Schema (Flyway Migrations)

Create `services/core/src/main/resources/db/migration/V1__initial_schema.sql`:

Implement ALL 12 tables from the data model:

| Table | Key columns | Notes |
|---|---|---|
| `scholar` | usid (UUID PK), demographics (JSONB), guardian_usid (FK nullable) | Main identity table |
| `scholar_system_link` | usid (FK), system_name, external_id, match_confidence, resolution_method | Links USID to NSP/SFMP/NOS IDs |
| `claim` | id (UUID PK), usid (FK), claim_type, value_encrypted (BYTEA), source, method, confidence, verified_at, valid_until | Verified Claims Wallet |
| `claim_attestation` | id (UUID PK), claim_id (FK), jws_token (TEXT), public_key_id | Signed attestations |
| `application` | id (UUID PK), usid (FK), scheme, academic_year, stage, current_actor, sla_deadline | Stage machine |
| `application_event` | id (BIGSERIAL PK), application_id (FK), stage, actor, timestamp, notes | **APPEND-ONLY** |
| `deficiency` | id (UUID PK), application_id (FK), usid (FK), type, message, resolution_route, status | Actionable fix for failures |
| `disbursement` | id (UUID PK), usid (FK), scheme, sanctioned_amount_paise (BIGINT), paid_amount_paise (BIGINT), pfms_ref, failure_code | Money tracking |
| `consent_artefact` | id (UUID PK), usid (FK), purpose, scope, granted_by, expires_at, revoked_at | DPDP consent |
| `access_audit` | id (BIGSERIAL PK), usid (FK), accessor, field_accessed, consent_id (FK), accessed_at | **APPEND-ONLY** |
| `scheme_rule_version` | id (UUID PK), scheme, academic_year, rules_json (JSONB) | Rules as data |
| `coverage_candidate` | id (UUID PK), hashed_key, state, district, block, school, class_level, gender, pvtg_status, outreach_status | Gap analysis |

**Critical constraints:**
- Append-only tables (`application_event`, `access_audit`): add a trigger or application-level guard that prevents UPDATE and DELETE
- All `*_paise` columns: `BIGINT NOT NULL DEFAULT 0`
- All timestamps: `TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- Create appropriate indexes on: usid, scheme, academic_year, stage, system_name+external_id, hashed_key

---

#### TASK 1.3 through TASK 1.9 — Core Service Implementation

Implement each service following the architecture described in the execution plan. For each:

**TASK 1.3 — USID Identity Resolution Service:**
- 3-stage resolver: deterministic (OTR/Aadhaar ref) → probabilistic (Jaro-Winkler, Fellegi-Sunter) → human adjudication
- Call AI service for fuzzy matching scores
- Write `scholar` + `scholar_system_link` records
- Detect duplicate beneficiaries as a side-effect
- Endpoint: `POST /v1/identity/resolve`

**TASK 1.4 — Verified Claims Wallet:**
- Claim CRUD with typed claims (identity, st_status, income, domicile, academic, enrolment, institution, net_jrf, disability, bank_account)
- Validity windows, auto-expiry
- Field-level encryption via Aadhaar Data Vault pattern
- Renewal: if all claims valid → zero-document confirmation
- Endpoint: `GET /v1/scholars/{usid}/claims`

**TASK 1.5 — Verification Orchestrator:**
- Strategy pattern: ordered chain of Tier 1-4 per claim type
- Each adapter calls govsim (or real DigiLocker via API Setu)
- Circuit breakers per adapter (Resilience4j)
- Retries with exponential backoff + jitter
- Idempotency keys on every write
- Response cache (Redis) with per-claim TTL
- NEVER block applicant on failure → create deficiency
- Immutable provenance record on every attempt
- Endpoint: `POST /v1/verify`

**TASK 1.6 — Eligibility & Conflict Engine:**
- Load rules from `packages/rules/` JSON files
- Generic rule evaluator: supports operators `in`, `lte`, `gte`, `eq`, `exists`
- Evaluate claims wallet against rules
- Return: per scheme → `eligible` / `not_eligible` / `missing_items` with reasons
- "What changes if" simulator
- Compatibility matrix enforcement
- Endpoint: `POST /v1/eligibility/evaluate`

**TASK 1.7 — Application & SLA Tracker:**
- State machine: submitted → institute_verification → district_nodal → state_dept → ministry → pfms_payment → disbursed
- SLA deadlines per stage (configurable)
- Delay-risk scoring from stage timestamps
- Append-only events
- Endpoint: `GET /v1/applications/{id}/timeline`

**TASK 1.8 — DBT Failure Doctor:**
- Failure taxonomy mapping PFMS/SFMP/NPCI rejection codes to: cause, fix, location
- Top 5 codes with full resolution instructions
- Endpoint: `GET /v1/disbursements/{usid}`

**TASK 1.9 — Consent & Audit Ledger:**
- DPDP Act 2023 compliant
- Guardian-linked family accounts (Pre-Matric = minors)
- Purpose-bound consent artefacts with expiry
- Per-fetch consent, not blanket
- One-tap revocation
- "Who looked at my data" audit trail
- Endpoints: `POST /v1/consent`, `DELETE /v1/consent/{id}`, `GET /v1/scholars/{usid}/audit`

---

### PHASE 2 — AI SERVICES (FastAPI / Python 3.13)

---

#### TASK 2.1 — FastAPI Project Setup

Initialize `services/ai/`:

```
services/ai/
├── pyproject.toml
├── Dockerfile
├── app/
│   ├── main.py              # FastAPI app
│   ├── config.py            # Settings (pydantic-settings)
│   ├── routers/
│   │   ├── rag.py           # Scheme RAG endpoints
│   │   ├── jago.py          # JAGO+ tool router
│   │   ├── docai.py         # Document AI endpoints
│   │   ├── matching.py      # Fuzzy match scorer
│   │   └── gap.py           # Coverage gap analytics
│   ├── services/
│   │   ├── rag_service.py
│   │   ├── jago_service.py
│   │   ├── docai_service.py
│   │   ├── matching_service.py
│   │   └── gap_service.py
│   ├── models/              # pydantic v2 models
│   └── db/                  # pgvector connection
└── tests/
```

**Dependencies (`pyproject.toml`):**
```toml
[project]
requires-python = ">=3.13"
dependencies = [
    "fastapi>=0.115",
    "uvicorn[standard]",
    "pydantic>=2.0",
    "pydantic-settings",
    "sqlalchemy>=2.0",
    "pgvector",
    "httpx",
    "python-multipart",
    "pillow",
    "pytesseract",
    "langchain-core",
    "langchain-community",
]
```

#### TASK 2.2 — Scheme RAG with Clause Citations
- Ingest 5 scheme guideline PDFs → chunk → embed → pgvector
- Query returns answers with clause citations: "As per Section 4.2 of the Pre-Matric guidelines..."
- NEVER invent eligibility rules

#### TASK 2.3 — JAGO+ Tool Router
- 7 tools: get_my_applications, check_eligibility, explain_deficiency, why_is_payment_pending, next_action, list_required_documents, get_disbursement_history
- Each tool calls the Core service API and returns STRUCTURED output
- **Status answers are TEMPLATE-FILLED, NEVER free-generated**

#### TASK 2.4 — Doc AI (OCR, Parse, Tamper)
- OCR: Tesseract or Google Vision API
- Field extraction from Indian government certificates
- Issuer-format validation, checksum verification, tamper signals
- Feeds into Verification Orchestrator Tier 3

#### TASK 2.5 — Fuzzy Match Scorer
- Jaro-Winkler on normalised names with Indic transliteration folding
- Weighted scoring across 7 fields
- Returns confidence + per-field breakdown
- Called by USID resolver

#### TASK 2.6 — Coverage Gap Engine
- Left-anti-join: UDISE+/APAAR students vs NSP applicants
- **Privacy-preserving:** HMAC of Aadhaar ref under shared rotating salt
- Only hashed keys compared — no raw PII
- Slice by state, district, block, school, class, gender, PVTG
- Endpoint: `GET /v1/admin/coverage-gap`

---

### PHASE 3 — GOVSIM MOCK SERVICES

---

#### TASK 3.1 — GoVSim Server

Create `services/govsim/` as Node.js + Express:

**7 mock government systems, deliberately hostile:**

| System | Response Format | Quirk |
|---|---|---|
| NSP | SOAP-ish XML | OTR-based lookup, legacy format |
| SFMP (Canara Bank) | CSV-in-JSON | Flat records, different ID scheme |
| NOS | Paginated JSON | DIFFERENT date format on each page (DD/MM/YYYY, YYYY-MM-DD, epoch) |
| UDISE+/APAAR | JSON | Student enrolment, AISHE codes |
| PFMS/DBT | JSON | Payment status, full rejection code taxonomy |
| UGC-NTA | JSON | NET/JRF results |
| DigiLocker proxy | JSON | Fallback when API Setu sandbox is down |

**Injectable chaos (via `X-Chaos` header or env config):**
- 8% random HTTP 500s
- 1.5–4 second latency spikes (random)
- Occasional truncated JSON payloads
- Rate limit at 20 requests/minute with 429 responses

**Deliberately dirty data:**
- Same student as "Sunita Meena" in NSP and "Sunita K. Mina" in SFMP
- Missing Aadhaar on 15% of legacy rows
- 40 genuine duplicate beneficiaries detectable by cross-system query
- Students in UDISE+ with zero NSP applications (the coverage gap)

**Document EVERYTHING in `docs/specs/govsim-contracts.md`** — this doubles as the integration-readiness annexe in the presentation.

---

### PHASE 6 — INTEGRATION (Your Parts)

---

#### TASK 6.1 — DigiLocker Live Integration

Wire the REAL DigiLocker integration via API Setu sandbox:
- OAuth 2.0 flow implementation in the DigiLocker adapter
- Pull certificates: caste/community, income, domicile, academic marksheets
- This is the ONE genuinely live integration in the demo
- Test with API Setu sandbox sample data
- Fallback: if sandbox is down during judging, the govsim DigiLocker proxy kicks in

#### TASK 6.2 — Bhashini Language Integration

Wire Bhashini ULCA REST APIs in the AI service:
- ASR endpoint for voice-to-text
- NMT endpoint for translation (22 scheduled languages)
- TTS endpoint for text-to-speech
- Minimum 3 languages working: Hindi, English, + 1 tribal language
- Free onboarding — use Bhashini API keys

#### TASK 6.3 — E2E Demo Path

Create `docs/specs/demo-path.md` and implement the E2E test that validates the 5-minute demo script:

1. USID resolver links one student across NSP/SFMP/NOS records, catches a duplicate
2. DigiLocker sandbox pull → claim verified → same claim satisfies a second scheme → renewal with zero uploads
3. Force a name mismatch → verify actionable deficiency is created (not a dead end)
4. JAGO+ tool call → structured status response → template-filled answer
5. Coverage gap query → returns school-level lists with counts
6. Exception queue → STP-eligible file found → approval flow

**The entire demo works with WIFI OFF except the DigiLocker beat.**

Add as `make e2e` target.

---

## ✅ DEFINITION OF DONE — Per Task

- [ ] Compiles / builds without errors
- [ ] Tests pass (unit + integration)
- [ ] OpenAPI contract unchanged OR regenerated (`make api`)
- [ ] Flyway migrations are forward-only (no editing existing migrations)
- [ ] No real PII anywhere in code or test data
- [ ] Aadhaar references encrypted at rest
- [ ] Money in integer paise everywhere
- [ ] Dates with explicit timezone everywhere
- [ ] Java: records for DTOs, no Lombok
- [ ] Python: pydantic v2, ruff clean
- [ ] Demo path still works end-to-end

---

## 🎯 PRIORITY ORDER IF TIME IS SHORT

If you're running out of time, build in this order (most demo-critical first):

1. ✅ OpenAPI contract (everything depends on this)
2. ✅ Docker infrastructure (team can't develop without it)
3. ✅ GoVSim (mocks needed before any integration testing)
4. ✅ USID resolver (the "identity" demo beat)
5. ✅ Claims Wallet + Verification Orchestrator (the "verify once" demo beat)
6. ✅ Eligibility Engine (the "5 schemes" demo beat)
7. ✅ DBT Failure Doctor (the "why payment stuck" demo beat)
8. ✅ DigiLocker integration (the LIVE demo beat)
9. ⬜ Coverage Gap Engine (the "Ministry impact" beat)
10. ⬜ JAGO+ Tool Router (the "voice" demo beat)
11. ⬜ Consent & Audit (important but not in the 5-min demo focus)
12. ⬜ Scheme RAG (stretch — enhances JAGO+ but not critical for demo)

Start with **TASK 0.1** and proceed in order. After each task, confirm completion before moving to the next.
