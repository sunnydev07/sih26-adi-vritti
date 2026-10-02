# E2E Demo Path — 5 Minutes, WIFI OFF (except the DigiLocker beat)

`make e2e` runs this path (`services/core e2eTest`).

## Beat 1 (30s) — The gap
2.43 crore ST students enrolled; 35–55% missing from portals; 3 disconnected
systems. Query: `GET /v1/admin/coverage-gap?district=Mandla`.

## Beat 2 (45s) — USID resolver links one student, catches a duplicate
`POST /v1/identity/resolve` with NSP (`Sunita Meena`) + SFMP (`Sunita K. Mina`)
records → one USID, confidence shown; duplicate beneficiary flagged.

## Beat 3 (75s) — Verify once, reuse everywhere; failures become deficiencies
1. DigiLocker (govsim) pull → `POST /v1/verify` (income claim) → `verified`. The
   adapter sources `family_income_annual_paise`, `st_or_pvtg_status` and
   `class_level`, and those land in the claims wallet — only adapter-derived values
   are ever persisted, so the API caller cannot invent one.
2. Same claim satisfies a second scheme (`GET /v1/scholars/{usid}/claims` shows the
   wallet; `POST /v1/eligibility/evaluate` is where the rule actually bites).
3. Force a name mismatch → actionable deficiency created, application NOT blocked.

Note: every personal-data read in this beat is consent-gated. A fresh stack has no
consent artefacts, so a read returns 403 `CONSENT_REQUIRED` until one exists —
`make seed` creates three read-path consents per seeded scholar. Both the grant and
the denial land in `access_audit`.

## Beat 4 (60s) — JAGO+ grounded status + DBT Doctor fix
`POST /v1/jago/tool/why_is_payment_pending` → structured output → template-filled
answer. `GET /v1/disbursements/{usid}` → `E001_AADHAAR_NOT_SEEDED` decoded with
nearest-branch fix instructions.

## Beat 5 (60s) — Coverage gap map + exception queue with STP scores
`GET /v1/admin/coverage-gap` → school-level lists. Note `enrolled_students` and
`applicants` are **0**: UDISE enrolment lives in govsim and applications carry no
school, so Core has no denominator and says so rather than inventing one.
`GET /v1/admin/exceptions` → rows carry a computed `stp_score` (0–100, heuristic:
stage base − SLA risk − open deficiency) and sort by `breach_risk` descending.
There is **no approve endpoint** — the officer console renders demo data and labels
it as such. Its `/dashboard` is behind a signed session cookie
(`OFFICER_SESSION_SECRET`, HS256, 8h); in development the login page offers a
one-click demo entry, and a tampered or expired cookie redirects to `/login`.

## Beat 6 (30s) — Integration-readiness matrix
| Integration | Status |
|---|---|
| DigiLocker via API Setu sandbox | Contract-ready (govsim) |
| NSP / SFMP / NOS / PFMS / UGC-NTA | Contract-ready (govsim) |
| Bhashini ASR/NMT/TTS (Hindi, English + tribal) | Contract-ready |

Nothing here is a live government integration: every "sandbox" row is govsim, which
returns synthetic responses with an 8% injected failure rate.

Offline note: the entire demo runs WIFI OFF except Beat 3 step 1.
