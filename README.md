# Adi-Vritti — Unified Scholarship App for Tribal Students

> **SIH 2026 · Problem Statement 26238.** One student. One identity. Five schemes.
>
> 🟢 **Live demo (officer dashboard):** https://sih26-adi-vritti-officer-web.vercel.app

Adi-Vritti unifies **5 Ministry of Tribal Affairs scholarship schemes**
(Pre-Matric, Post-Matric, Top Class, NFST, NOS) spread across **3 disconnected
portals** (NSP, SFMP, NOS) into one app for Scheduled Tribe students — with an
officer console for verification, disbursement tracking, and coverage-gap
analysis.

## The 9 layers

| # | Layer | What it does |
|---|---|---|
| 1 | **USID** | Unified Scholar ID via 3-stage identity resolution (deterministic → probabilistic → human adjudication) |
| 2 | **Verified Claims Wallet** | Verify a document once, reuse across all 5 schemes until expiry |
| 3 | **Verification Orchestrator** | 4-tier strategy chain: gov-verified → corroborated → assisted → pending-review |
| 4 | **Eligibility & Conflict Engine** | Rules as versioned JSON data (`packages/rules/<AY>/`), not code |
| 5 | **SLA & Deficiency Loop** | Stage machine with per-actor SLA deadlines; a failed verification creates a deficiency — never blocks the application |
| 6 | **DBT Failure Doctor** | Decodes PFMS/NPCI rejection codes (`E001`–`E006`) into plain-language fixes |
| 7 | **JAGO+ Scholarship Skill** | Tool-calling for the JAGO chatbot. Status answers are template-filled from tool output — the LLM never free-generates a status, amount, or verdict |
| 8 | **Coverage Gap Engine** | Privacy-preserving cross-ministry join via HMAC-hashed keys under a shared rotating salt. No raw PII crosses ministries |
| 9 | **DPDP Consent** | Guardian-linked family accounts (Pre-Matric = minors), purpose-bound consent artefacts, append-only access audit |

## Repo structure

```
apps/
  officer-web/     Next.js 16 officer console (deployed on Vercel)
  mobile/          Expo student app (offline-first)
services/
  core/            Spring Boot 3 / Java 21 API  → :8080
  ai/              FastAPI matching / gap / JAGO / DocAI / RAG  → :8000
  govsim/          7 hostile mock government systems (chaos + dirty data)  → :4000
packages/
  rules/           Versioned scheme eligibility JSON + compatibility matrix
  api-client/      Generated TS + Java clients (never hand-edit)
data/synthetic/    Seeded fake-data generator (seed 26238 → CSV + seed.sql)
docs/
  openapi/core.yaml  THE contract — all 13 endpoints
  specs/           System overview, demo path, GoVSim quirk catalogue
  adr/             Architecture decision records
infra/             docker-compose (postgres + pgvector, redis, minio, services)
```

## Quickstart

Prereqs: JDK 21, Python 3.13, Node 22, Docker.

```bash
make dev      # boot postgres, redis, minio, govsim, core, ai
make seed     # load deterministic synthetic data (seed 26238)
make test-core && make test-ai   # unit tests
make e2e      # 5-minute judging demo path (docs/specs/demo-path.md)
make api      # regenerate API clients from the OpenAPI contract
```

| Service | Local URL |
|---|---|
| Officer web | http://localhost:3000 (`npm run dev` in `apps/officer-web`) |
| Core API | http://localhost:8080 |
| AI services | http://localhost:8000/docs |
| GoVSim mocks | http://localhost:4000/health |

## Non-negotiables

- `docs/openapi/core.yaml` is the contract. Generate clients; never hand-edit `packages/api-client`.
- No mock data in `apps/` or `services/core` — all fake government data lives behind `services/govsim`.
- No real PII, ever. Test data comes from `data/synthetic` only.
- Never store an Aadhaar number in plaintext — reference keys only.
- Money in integer **paise**. Dates ISO-8601 with explicit zone.

## Deployment

- **Officer web** → Vercel, auto-deploys from `main` (root directory `apps/officer-web`).
- **Backend** → `infra/docker-compose.yml` (or any Docker host) for core + ai + govsim + postgres/redis/minio.
- **Mobile** → Expo EAS builds.

## Integration readiness

| Integration | Status |
|---|---|
| DigiLocker via API Setu sandbox | LIVE |
| NSP / SFMP / NOS / PFMS / UGC-NTA | Contract-ready (GoVSim) — see `docs/specs/govsim-contracts.md` |
| Bhashini ASR/NMT/TTS (Hindi, English + tribal languages) | Contract-ready |
