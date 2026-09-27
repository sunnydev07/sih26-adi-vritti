# E2E Demo Path — 5 Minutes, WIFI OFF (except the DigiLocker beat)

`make e2e` runs this path (`services/core e2eTest`).

## Beat 1 (30s) — The gap
2.43 crore ST students enrolled; 35–55% missing from portals; 3 disconnected
systems. Query: `GET /v1/admin/coverage-gap?district=Mandla`.

## Beat 2 (45s) — USID resolver links one student, catches a duplicate
`POST /v1/identity/resolve` with NSP (`Sunita Meena`) + SFMP (`Sunita K. Mina`)
records → one USID, confidence shown; duplicate beneficiary flagged.

## Beat 3 (75s) — Verify once, reuse everywhere; failures become deficiencies
1. DigiLocker sandbox pull → `POST /v1/verify` (income claim) → `verified`.
2. Same claim satisfies a second scheme (`GET /v1/scholars/{usid}/claims`).
3. Force a name mismatch → actionable deficiency created, application NOT blocked.

## Beat 4 (60s) — JAGO+ grounded status + DBT Doctor fix
`POST /v1/jago/tool/why_is_payment_pending` → structured output → template-filled
answer. `GET /v1/disbursements/{usid}` → `E001_AADHAAR_NOT_SEEDED` decoded with
nearest-branch fix instructions.

## Beat 5 (60s) — Coverage gap map + exception queue with STP scores
`GET /v1/admin/coverage-gap` → school-level lists (`Govt HS Bichhiya, Mandla:
47 ST students Class IX, 6 applications`). `GET /v1/admin/exceptions` →
STP-eligible file → one-click approve.

## Beat 6 (30s) — Integration-readiness matrix
| Integration | Status |
|---|---|
| DigiLocker via API Setu sandbox | LIVE |
| NSP / SFMP / NOS / PFMS / UGC-NTA | Contract-ready (govsim) |
| Bhashini ASR/NMT/TTS (Hindi, English + tribal) | Contract-ready |

Offline note: the entire demo runs WIFI OFF except Beat 3 step 1.
