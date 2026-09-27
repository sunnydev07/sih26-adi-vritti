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
| 6 | UGC-NTA | `/ugc-nta` | JSON | NET/JRF results; flaky (~40% negative) |
| 7 | DigiLocker proxy | `/digilocker` | JSON | Fallback when the API Setu sandbox is down; swap for live in TASK 6.1 |

## Chaos profile

| Fault | Rate | Mitigation under test |
|---|---|---|
| HTTP 500 | 8% (`CHAOS_ERROR_RATE`) | Retry with exponential backoff + jitter |
| Latency spike 1.5–4s | 10% of requests | 5s timeout + circuit breaker per adapter |
| Truncated JSON | 2% | Schema validation → degrade to next tier |
| Rate limit 429 | >20 RPM per IP | Backoff; `retry_after_seconds: 30` |

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
