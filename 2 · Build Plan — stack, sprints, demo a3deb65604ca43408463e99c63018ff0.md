# 2 · Build Plan — stack, sprints, demo

## Stack

| Layer | Choice | Why this one |
| --- | --- | --- |
| Mobile | React Native + Expo dev client, TypeScript | Team already picked RN; one codebase, fast iteration, mature camera and ML ecosystem |
| Local store | expo-sqlite + Drizzle, MMKV, TanStack Query with persistence | Offline-first reads, outbox writes, delta sync |
| Scanning | react-native-vision-camera + ML Kit text recognition | On-device OCR works with zero network |
| Core backend | **Spring Boot 3 / Java 21**, Postgres, Redis | Your strongest stack — and it is what NIC-grade government systems actually run on, which is a credibility signal in the room |
| AI services | FastAPI / Python 3.13, pgvector | RAG, doc parsing, match scoring, gap analytics |
| `govsim` | Node + Express | Fake but hostile stand-ins for 6 government systems |
| Officer dashboard | Next.js + shadcn/ui | Fast to build, looks institutional |
| Auth | JWT + OTP for demo; DigiLocker OAuth for the real identity beat | No Aadhaar auth — we are not a KUA |
| Files | MinIO, S3-compatible, client-side encrypted |  |
| Queue | Redis Streams | Verification jobs, outbox drain, retries |
| Signing | Ed25519 JWS for claim attestations | The credential-issuer story, in ~40 lines |
| Language | Bhashini ULCA REST for ASR / NMT / TTS | Live integration, free onboarding |

<aside>
⚠️

Two backend languages is a real cost. It is worth it only because a six-person team can parallelise, and because Spring Boot plays to the team's depth while Python owns the AI work. If the team is thin on Python, collapse the AI service into Spring Boot with an HTTP call out to the LLM and keep only the RAG index in Python.

</aside>

## Repo layout

```
adi-vritti/
  docs/
    specs/              # write these BEFORE code; the agent generates from them
    openapi/core.yaml   # single source of truth for all contracts
    adr/                # one file per architecture decision
  apps/
    mobile/             # React Native + Expo
    officer-web/        # Next.js dashboard
  services/
    core/               # Spring Boot: USID, claims, eligibility, applications, consent
    ai/                 # FastAPI: RAG, doc AI, match scorer, coverage gap
    govsim/             # mock NSP, SFMP, NOS, UDISE+, APAAR, PFMS, UGC-NTA
  packages/
    api-client/         # generated from openapi/core.yaml — never hand-edited
    rules/              # scheme rule JSON, versioned by academic year
  data/
    synthetic/          # generator + seed; deterministic, reproducible
  infra/
    docker-compose.yml  # postgres, redis, minio, govsim — one command to boot
```

## Contract-first API surface

Write `openapi/core.yaml` first, generate the TypeScript and Java clients from it, and let no one hand-edit a client. This is the single highest-leverage decision for a six-person parallel build.

| Endpoint | Purpose |
| --- | --- |
| `POST /v1/identity/resolve` | Returns USID plus linked system records and confidences |
| `GET /v1/scholars/{usid}/dashboard` | The one call that powers the home screen |
| `POST /v1/verify` | Claim verification through the tiered orchestrator |
| `GET /v1/scholars/{usid}/claims` | Verified Claims Wallet with validity windows |
| `POST /v1/eligibility/evaluate` | Per-scheme verdict, reasons, missing items |
| `GET /v1/applications/{id}/timeline` | Stage history, current actor, SLA state |
| `GET /v1/disbursements/{usid}` | Payments, DBT status, decoded failure reason |
| `POST /v1/jago/tool/{name}` | The tool surface JAGO calls |
| `POST /v1/consent` / `DELETE /v1/consent/{id}` | Grant and revoke consent artefacts |
| `GET /v1/admin/coverage-gap` | Hashed-key gap query, sliced by geography |
| `GET /v1/admin/exceptions` | Manual-review queue |

## govsim: build the mock hostile on purpose

Realistic mocks are worth more than pretty ones, and saying *why* they are hostile is a differentiator in itself — it proves the resilience design is real rather than decorative.

- NSP endpoint returns SOAP-ish XML; SFMP returns a flat CSV-in-JSON; NOS returns paginated records with a different date format on each. Because that is what legacy integration is like.
- Injectable chaos via header or config: 8% 500s, 1.5–4 s latency spikes, occasional truncated payloads, rate limit at 20 rpm.
- Deliberately dirty data: name variants across systems, missing Aadhaar on legacy rows, one human present in two systems under two spellings, a genuine duplicate beneficiary, students enrolled in UDISE+ with no NSP record.
- Every quirk documented in `docs/specs/govsim-contracts.md`, so it doubles as the integration-readiness annexe in your PPT.

## Synthetic dataset spec

Never touch real PII. Generate, with a fixed seed so results are reproducible during judging:

| Slice | Volume | Why |
| --- | --- | --- |
| ST students in the UDISE+ / APAAR mock | 50,000 across 4 states, 12 districts, ~300 schools | Makes the coverage-gap map look like a real country |
| NSP applications | 18,000 across Pre-Matric, Post-Matric, Top Class | Leaves a visible, sliceable coverage gap |
| SFMP fellowship records | 400 NFST scholars | Small-n, like reality |
| NOS records | 60 across three selection years | 20 awards a year |
| Deliberate identity puzzles | 1,200 | Fuzzy matcher has something to actually solve |
| Duplicate beneficiaries | 40 | Demo the conflict detector |
| Failed disbursements | 900, spread across the full rejection-code taxonomy | Demo the DBT Failure Doctor |

## Before the finale

Most teams lose the finale in the weeks before it. Use them.

| Week | Goal | Done looks like |
| --- | --- | --- |
| 1 | Foundations | `docker-compose up` boots everything; `openapi/core.yaml` frozen v1; synthetic generator produces the full dataset; govsim answers all 7 systems |
| 2 | The core that wins | USID resolver with a measurable precision/recall number on the synthetic puzzles; claim wallet; verification orchestrator with all 4 tiers; DigiLocker sandbox call working end to end |
| 3 | Eligibility, JAGO+, gap | Rule JSON for all 5 schemes; scheme-guideline RAG with clause citations; tool router; coverage-gap query with hashed-key join; officer dashboard shell |
| 4 | Demo hardening | Mobile UI polished on a 2 GB Android profile; Bhashini voice path working in 3 languages; DBT Doctor; demo rehearsed 5 times with wifi off |

**Measure the USID resolver.** Walking in with "96.4% precision, 91% recall on 1,200 synthetic identity puzzles, 3% routed to human review" beats every polished slide in the room, because nobody else will have a number.

## The 36-hour runbook

Assumes the pre-finale work exists. If it does not, cut ruthlessly to the Must-build list below.

| Hours | Focus |
| --- | --- |
| 0–2 | Boot infra, seed data, assign worktrees, freeze the contract. No architecture debates after hour 2. |
| 2–8 | Parallel: core endpoints · mobile dashboard · govsim gaps · RAG index |
| 8–14 | Verification orchestrator end to end, including the live DigiLocker sandbox call |
| 14–18 | Eligibility engine plus the conflict matrix wired into the mobile UI |
| 18–22 | JAGO+ tool calling and the Bhashini voice round trip |
| 22–26 | DBT Failure Doctor plus the SLA timeline |
| 26–30 | Officer dashboard: exception queue, STP score, coverage-gap map |
| 30–33 | **Freeze code.** Demo-path smoke test, offline test with wifi off, load the demo data |
| 33–36 | Rehearse the 5-minute script until it is muscle memory. Prepare the Q&A sheet. |

## Scope guardrails

**Must build — this is the demo:**

- USID resolution with a visible confidence score and a human-review queue
- Verified Claims Wallet plus the verification orchestrator with all four tiers and provenance records
- One **genuinely live** integration: DigiLocker via the API Setu sandbox
- Eligibility and conflict verdicts for all five schemes from rule JSON
- Unified mobile dashboard: status, SLA, pending action, money received versus pending
- JAGO+ with tool calling and templated status answers in at least three languages, one by voice
- DBT Failure Doctor for the top five rejection causes
- Officer dashboard with the exception queue and the coverage-gap map

**Stretch, only if ahead:** signed JWS attestations · Adi Vaani tribal-language path · SMS/IVR fallback · assisted batch mode · delay-risk scoring · document tamper signals

**Pitch only — do not attempt to build:** Aadhaar eKYC or Face RD, we are not a KUA and saying so earns credit · live NSP / SFMP / NOS writes · real UDISE+ data · any claim of automatic sanction authority — always "decision support for the officer"

## Demo script, 5 minutes

| Beat | Time | What happens |
| --- | --- | --- |
| The gap | 30 s | 2.43 crore ST students enrolled. CAG measured 35–55% of eligible ST students missing from Pre-Matric. Three portals, five schemes, no single view. |
| One identity | 45 s | Show the resolver linking one student's NSP, SFMP and NOS records, with confidence scores — and a genuine duplicate beneficiary it catches. State the precision number. |
| Verify once, reuse | 75 s | Live DigiLocker sandbox pull → claim verified with provenance → same claim auto-satisfies a second scheme's requirement → renewal with zero uploads. Then break it on purpose: force a name mismatch and show the actionable deficiency instead of a dead end. |
| The student's answer | 60 s | Voice question in Hindi, then one in a tribal language → grounded status answer with the pending actor and days elapsed → then "why is my payment stuck" → DBT Doctor's specific fix. |
| The Ministry's answer | 60 s | Coverage-gap map down to one school. Explain the hashed-key join in one sentence. Exception queue plus STP score. |
| What is real | 30 s | Integration readiness matrix on screen. Name exactly what is live today, what is contract-ready, and the one MoU each needs. |

Do the whole thing with **wifi off** except for the DigiLocker beat. Surviving a dead network on stage is itself a feature demonstration.

## Risk register

| Risk | Mitigation |
| --- | --- |
| DigiLocker sandbox down during judging | Record a 20-second screen capture of a successful live call the night before; play it and say it is a fallback recording |
| Venue network unusable | Everything but one beat runs offline; `docker-compose` on a laptop, phone on USB tethering |
| Two-language backend eats time | Pre-finale weeks exist for exactly this; if week 2 slips, collapse AI into Spring Boot |
| Judge asks for a real NSP integration | Answer with the readiness matrix and the MoU path, not an apology |
| Scope creep into a pretty UI | UI freeze at hour 30; polish only the six screens in the demo script |
| LLM hallucinating a scholarship status on stage | Status answers are templated from tool output by design, so this cannot happen — say that out loud |