# GoVSim Contracts — Integration-Readiness Annexe

Every quirk below is deliberate: Core adapters must prove resilience against it.
Core treats govsim EXACTLY like real government APIs (timeouts, circuit breakers,
retries with jitter, idempotency keys). Disable chaos per-request with
`X-Chaos: off`, or globally with `CHAOS_ENABLED=false`.

## Systems

| # | System | Base path | Response format | Quirk |
|---|---|---|---|---|
| 1 | NSP | `/nsp` | SOAP-ish XML flavour in JSON envelope | OTR-based lookup; 15% legacy rows have `aadhaar_ref_key: null` |
| 2 | SFMP (Canara Bank) | `/sfmp` | CSV-in-JSON | Flat records, different ID scheme (`SFMP-######`); different name spelling than NSP |
| 3 | NOS | `/nos` | Paginated JSON | DIFFERENT date format per page: p1 `DD/MM/YYYY`, p2 `YYYY-MM-DD`, p3 epoch |
| 4 | UDISE+/APAAR | `/udise` | JSON | Enrolment + AISHE codes; includes students with zero NSP record (the coverage gap) |
| 5 | PFMS/DBT | `/pfms` | JSON | Full 6-code rejection taxonomy (`E001`–`E006`) |
| 6 | UGC-NTA | `/ugc-nta` | JSON | NET/JRF results; ~40% verify positive (deterministic per USID) |
| 7 | DigiLocker proxy | `/digilocker` | JSON | Fallback when the API Setu sandbox is down; swap for live in TASK 6.1. Deterministic per USID: demo student always verifies, ~1 in 8 other USIDs rejected with `reason_code: DOCUMENT_MISMATCH` |

## Chaos profile

| Fault | Rate | Mitigation under test |
|---|---|---|
| HTTP 500 | 8% (`CHAOS_ERROR_RATE`) | Retry with exponential backoff + jitter |
| Latency spike 1.5–4s | 10% of requests | 5s timeout + circuit breaker per adapter |
| Truncated JSON | 2% | Schema validation → degrade to next tier |
| Rate limit 429 | >20 RPM per IP | Backoff; `retry_after_seconds: 30` |

## Deterministic verify outcomes

Transport chaos above stays random, but a verify DECISION is a pure function
of the USID (stable FNV-1a hash in `src/index.js`): the same USID gets the
same answer on every call and every run. Pass rates: NSP 75%, SFMP 65%,
NOS 50%, UGC-NTA 40%, DigiLocker-proxy 87.5%. The demo student
(`11111111-1111-4111-8111-111111111111`) always verifies, so the demo path is
stable while every system keeps a genuine rejection path.

A successful `/digilocker/verify` also carries deterministic document
`fields` — `{family_income_annual_paise, st_or_pvtg_status, class_level}` —
pure functions of the USID (demo student: `24000000` / `ST` / `9`). Core
persists these adapter-sourced values into the claims wallet; the verify
request itself carries no value, so callers can never invent one.

## Dirty-data catalogue (deterministic, seed 26238)

- `Sunita Meena` (NSP) = `Sunita K. Mina` (SFMP) = `SUNITA MEENA` (NOS).
- 40 duplicate beneficiaries detectable only by cross-system query.
- `Phulo Gond` (Govt HS Bichhiya, Mandla, Class 9): enrolled, no NSP record.
- 900 failed disbursements across the `E001`–`E006` taxonomy
  (30/20/15/20/10/5 split — see `data/synthetic/generate.py`).

## Endpoint index

`GET /nsp/verify /nsp/applications /sfmp/verify /sfmp/records /nos/verify
/nos/records?page=N /udise/verify /udise/students /pfms/verify /pfms/status
/ugc-nta/verify /digilocker/verify /health`
