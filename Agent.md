# Adi-Vritti

SIH 2026 PS 26238. Unified scholarship app for ST students across MoTA's five
schemes and three portals. Read docs/specs/00-overview.md before any task.

## Non-negotiables
- docs/openapi/core.yaml is the contract. Generate clients; never hand-edit
  packages/api-client.
- No mock or hardcoded data in apps/ or services/core. All fake government data
  lives behind services/govsim.
- No real PII, ever. Test data comes from data/synthetic with its fixed seed.
- Never store an Aadhaar number in plaintext. Reference keys only.
- Factual scholarship status is rendered from templates over tool output.
  The LLM never free-generates a status, amount, or eligibility verdict.
- Every verification writes an immutable provenance record. No silent failures.
- A verification failure never blocks an application. It creates a deficiency.

## Commands
- Boot everything: make dev
- Core tests: make test-core   AI tests: make test-ai   E2E: make e2e
- Regenerate clients after a contract change: make api
- Reseed synthetic data: make seed

## Conventions
- Java 21, Spring Boot 3, records for DTOs, no Lombok.
- Python 3.13, FastAPI, pydantic v2, ruff.
- TypeScript strict everywhere. No any.
- Money in integer paise. Dates as ISO-8601 with an explicit zone.
- Conventional commits. One workstream per branch.

## Definition of done
Compiles, tests pass, contract unchanged or regenerated, and the demo path in
docs/specs/demo-path.md still runs end to end.