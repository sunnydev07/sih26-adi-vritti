-- V3: scope the claim idempotency key by claim type.
--
-- V2 made (usid, idempotency_key) unique, but a caller key is only meaningful
-- within one operation: the same key string reused for a different claim_type
-- of the same scholar replayed the other type's claim -- same claim id,
-- wrong verdict, wrong type echoed back. The scope is now
-- (usid, claim_type, idempotency_key), matching the repository lookup
-- findFirstByUsidAndClaimTypeAndIdempotencyKey. claim_type is NOT NULL (V1),
-- so no data backfill is needed: the new index is strictly weaker than the
-- old one and every existing row already satisfies it.
--
-- Safe re-runs (CI applies each migration twice): DROP INDEX IF EXISTS plus
-- CREATE UNIQUE INDEX IF NOT EXISTS converge on the scoped definition.

DROP INDEX IF EXISTS uq_claim_idempotency;
CREATE UNIQUE INDEX IF NOT EXISTS uq_claim_idempotency
    ON claim (usid, claim_type, idempotency_key) WHERE idempotency_key IS NOT NULL;
