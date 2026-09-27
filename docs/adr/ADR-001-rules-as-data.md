# ADR-001 — Rules as Data, Not Code

Status: accepted. Date: 2026-09-27.

## Context
Scheme eligibility ceilings change per academic year. Hardcoded rules break every
cycle and need a release to fix.

## Decision
Eligibility rules live as versioned JSON in `packages/rules/<AY>/`, mirrored into
the `scheme_rule_version` table. `RuleEngine` evaluates generic operators
(`in`, `lte`, `gte`, `eq`, `exists`). A ceiling change is a config edit + Flyway
data migration, not a code release.

## Consequences
- Core must validate rule files against `packages/rules/schema.json` on startup.
- Frontend renders `reasons`/`missing_claims` verbatim — never invents verdicts.
