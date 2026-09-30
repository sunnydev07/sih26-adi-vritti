-- P2-6: the idempotencyKey fields the contract accepts are now honored.
-- identity_resolution replays a whole resolution; claim.idempotency_key
-- keeps a retried verification on the same wallet row.

CREATE TABLE IF NOT EXISTS identity_resolution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    usid UUID NOT NULL REFERENCES scholar(usid) ON DELETE CASCADE,
    -- Frozen copy of the original response, so a replay returns the same
    -- report instead of re-running (and possibly re-minting) the resolution.
    linked_records JSONB NOT NULL DEFAULT '[]'::jsonb,
    overall_confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    needs_human_review BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_usids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_identity_resolution_usid ON identity_resolution(usid);

ALTER TABLE claim ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
-- Partial index: NULL (no key supplied) never conflicts; a supplied key is
-- unique per scholar.
CREATE UNIQUE INDEX IF NOT EXISTS uq_claim_idempotency
    ON claim (usid, idempotency_key) WHERE idempotency_key IS NOT NULL;
