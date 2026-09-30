# ADR-002 — Accepted but unimplemented: signed claim attestations

Status: accepted. Date: 2026-09-30.

## Context

V1 ships a `claim_attestation` table (claim_id, Ed25519 JWS token, public_key_id,
revoked_at) with no writer and no reader anywhere in the codebase, and the
contract exposes no attestation field. P2 investigated wiring it minimally and
deliberately did not, for one reason: a signature nobody verifies is security
theater, and a signature with a committed dev key proves nothing. An
attestation subsystem is only real when key custody, verification, and
revocation all exist together.

## Decision

`claim_attestation` stays schema-only until all three re-activation criteria
hold. Until then, claim provenance is carried by the existing mechanisms that
are actually read: the verification provenance trail on every `VerifyResponse`,
the append-only `access_audit`, and the officer decision records.

## Re-activation criteria (all three, not one)

1. **Key ceremony**: Ed25519 keys live in a KMS/HSM (never in config or the
   repo), with a published `public_key_id` registry and rotation procedure.
2. **Verifier consumers**: at least one reader verifies the JWS before acting
   on it (e.g. an officer-console or inter-ministry consumer that rejects
   claims whose attestation does not verify or is revoked).
3. **Contract surface**: the attestation (or its verification verdict) is part
   of `docs/openapi/core.yaml`, so clients can rely on it and the
   contract-drift check pins it.

## Explicitly not deferred (done in P2, do not re-litigate)

- `scheme_rule_version`: written by `RuleBootstrapRunner` at startup from
  schema-validated rule files, read by `EligibilityService` as the disk-miss
  fallback (ADR-001).
- `idempotencyKey` on identity resolve and verify: stored and replayed
  (`identity_resolution` table, `claim.idempotency_key` partial unique index).
- `app.security.jwt.issuer`: enforced in the `JwtDecoder` when configured.
- Rule-files-vs-`schema.json` startup validation: `RuleValidator`, fail-closed.
- `packages/api-client`: regenerated and typechecked in CI (`make api`).

## Consequences

- New tables must ship with a writer, a reader, and a test in the same change;
  schema-only tables are rejected in review. This ADR is the precedent.
- When the criteria hold, the work is: key ceremony runbook + signer in the
  verification success path + verifier consumer + contract fields + drift
  coverage — estimated as its own workstream, not hygiene.
