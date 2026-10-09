-- V4__aadhaar_ref_unique.sql — one human being, one scholar row.
--
-- Identity resolution mints a scholar when no linked record and no known
-- Aadhaar reference key is found. That check-then-insert is a TOCTOU race:
-- two concurrent first-time resolves for the same person both saw "unknown"
-- and both minted, producing two USIDs for one human being and permanently
-- poisoning the duplicate graph. A database guard is the only correct fix —
-- application-level locking cannot span the read and the write.
--
-- Expression index on the JSONB reference key (partial: scholars without a
-- key, e.g. keyless re-enrolments, are exempt). The service retries a
-- violation once against the winner's committed row (see IdentityController).
CREATE UNIQUE INDEX IF NOT EXISTS uq_scholar_aadhaar_ref
    ON scholar ((demographics->>'aadhaarRefKey'))
    WHERE demographics->>'aadhaarRefKey' IS NOT NULL;
