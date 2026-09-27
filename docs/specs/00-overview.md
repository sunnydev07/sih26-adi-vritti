# Adi-Vritti — System Overview

> SIH 2026 · PS 26238 — Unified Scholarship App for Tribal Students.
> Tagline: One student. One identity. Five schemes.

Adi-Vritti unifies 5 MoTA scholarship schemes (Pre-Matric, Post-Matric, Top Class,
NFST, NOS) spread across 3 disconnected portals (NSP, SFMP, NOS) into one app for
Scheduled Tribe students.

## The 9 layers

1. **USID** — Unified Scholar ID via 3-stage identity resolution
   (deterministic → probabilistic → human adjudication).
2. **Verified Claims Wallet** — verify a document once, reuse across all 5 schemes
   until expiry.
3. **Verification Orchestrator** — 4-tier strategy chain
   (gov-verified → corroborated → assisted → pending-review).
4. **Eligibility & Conflict Engine** — rules as versioned JSON data, not code
   (`packages/rules/<AY>/*.json` + `scheme_rule_version` table).
5. **SLA & Deficiency Loop** — application stage machine with SLA deadlines per actor;
   a verification failure NEVER blocks an application, it creates a deficiency.
6. **DBT Failure Doctor** — decodes PFMS/NPCI rejection codes into plain-language fixes.
7. **JAGO+ Scholarship Skill** — tool-calling for the JAGO chatbot, NOT a competing
   chatbot. Status answers are template-filled from tool output; the LLM never
   free-generates a status, amount, or eligibility verdict.
8. **Coverage Gap Engine** — privacy-preserving cross-ministry join using HMAC
   hashed keys under a shared rotating salt. No raw PII crosses ministries.
9. **DPDP Consent** — guardian-linked family accounts (Pre-Matric = minors),
   purpose-bound consent artefacts, append-only access audit.

## Service map

| Service | Port | Stack |
|---|---|---|
| core | 8080 | Spring Boot 3 / Java 21 |
| ai | 8000 | FastAPI / Python 3.13 |
| govsim | 4000 | Node.js + Express (mock govt systems) |
| postgres | 5432 | Postgres 16 + pgvector |
| redis | 6379 | Redis 7 |
| minio | 9000/9001 | S3-compatible document store |

## Non-negotiables (from Agent.md)

- `docs/openapi/core.yaml` is the contract. Generate clients; never hand-edit
  `packages/api-client`.
- No mock/hardcoded data in `apps/` or `services/core`. All fake government data
  lives behind `services/govsim`.
- No real PII, ever. Test data from `data/synthetic` (seed 26238).
- Never store an Aadhaar number in plaintext. Reference keys only.
- Money in integer paise. Dates ISO-8601 with explicit zone.

## Read next

- `demo-path.md` — the 5-minute judging demo, beat by beat.
- `govsim-contracts.md` — integration-readiness annexe: every mock quirk documented.
- `../openapi/core.yaml` — THE contract.
