-- V1__initial_schema.sql — Adi-Vritti core data model (12 tables).
-- Conventions: money BIGINT paise, TIMESTAMPTZ dates, Aadhaar as ref keys only.
--
-- Every statement is idempotent (IF NOT EXISTS / OR REPLACE) so this migration can be
-- re-run against a partially-created database. `gen_random_uuid()` is built into
-- PostgreSQL 13+ and does NOT require uuid-ossp or pgcrypto.
--
-- NOTE: this file was rewritten before the service had ever been successfully
-- deployed, so the Flyway checksum history contains no prior version of it.

-- gen_random_uuid() is core since PG13; pgcrypto is requested only as a safety net
-- for older images and is a no-op when already present.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Scholar (USID identity)
CREATE TABLE IF NOT EXISTS scholar (
    usid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demographics JSONB NOT NULL DEFAULT '{}'::jsonb,
    guardian_usid UUID REFERENCES scholar(usid) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT scholar_demographics_is_object CHECK (jsonb_typeof(demographics) = 'object')
);

-- 2. Links USID -> external system IDs (NSP/SFMP/NOS/UDISE/APAAR)
CREATE TABLE IF NOT EXISTS scholar_system_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usid UUID NOT NULL REFERENCES scholar(usid) ON DELETE CASCADE,
    system_name VARCHAR(16) NOT NULL,
    external_id VARCHAR(64) NOT NULL,
    match_confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    resolution_method VARCHAR(32) NOT NULL DEFAULT 'deterministic',
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_syslink_system_external UNIQUE (system_name, external_id),
    CONSTRAINT syslink_confidence_range CHECK (match_confidence >= 0.0 AND match_confidence <= 1.0)
);
CREATE INDEX IF NOT EXISTS idx_syslink_usid ON scholar_system_link(usid);

-- 3. Verified Claims Wallet
CREATE TABLE IF NOT EXISTS claim (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usid UUID NOT NULL REFERENCES scholar(usid) ON DELETE CASCADE,
    claim_type VARCHAR(32) NOT NULL,
    value_encrypted BYTEA NOT NULL,
    source VARCHAR(64) NOT NULL,
    method VARCHAR(32) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until TIMESTAMPTZ NOT NULL,
    evidence_ref VARCHAR(512),
    verifier VARCHAR(128),
    CONSTRAINT claim_confidence_range CHECK (confidence >= 0.0 AND confidence <= 1.0),
    CONSTRAINT claim_validity_window CHECK (valid_until > verified_at)
);
CREATE INDEX IF NOT EXISTS idx_claim_usid ON claim(usid);
CREATE INDEX IF NOT EXISTS idx_claim_type_valid ON claim(claim_type, valid_until);
-- Note: a partial index cannot use now() in its predicate ("functions in index
-- predicate must be marked IMMUTABLE"). Expiry filtering therefore happens in the
-- query, backed by the (usid, valid_until) composite index above.

-- 4. Signed claim attestations (Ed25519 JWS)
CREATE TABLE IF NOT EXISTS claim_attestation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id UUID NOT NULL REFERENCES claim(id) ON DELETE CASCADE,
    jws_token TEXT NOT NULL,
    public_key_id VARCHAR(128) NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_attestation_claim ON claim_attestation(claim_id);

-- 5. Application stage machine
CREATE TABLE IF NOT EXISTS application (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usid UUID NOT NULL REFERENCES scholar(usid) ON DELETE CASCADE,
    scheme VARCHAR(16) NOT NULL,
    academic_year VARCHAR(8) NOT NULL,
    stage VARCHAR(32) NOT NULL DEFAULT 'submitted',
    current_actor VARCHAR(64) NOT NULL DEFAULT 'institute',
    sla_deadline TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT application_year_format CHECK (academic_year ~ '^[0-9]{4}-[0-9]{2}$')
);
CREATE INDEX IF NOT EXISTS idx_application_usid ON application(usid);
CREATE INDEX IF NOT EXISTS idx_application_scheme_year ON application(scheme, academic_year);
CREATE INDEX IF NOT EXISTS idx_application_stage ON application(stage);
-- The exception queue orders by breach risk: soonest deadline first.
CREATE INDEX IF NOT EXISTS idx_application_sla ON application(sla_deadline) WHERE sla_deadline IS NOT NULL;

-- 6. Application events — APPEND-ONLY
CREATE TABLE IF NOT EXISTS application_event (
    id BIGSERIAL PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES application(id) ON DELETE CASCADE,
    stage VARCHAR(32) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes TEXT
);
CREATE INDEX IF NOT EXISTS idx_appevent_app ON application_event(application_id);

-- 7. Deficiencies (verification failure -> actionable fix, never a block)
-- application_id is NULLABLE on purpose: a student can add a claim to the wallet
-- and hit a verification failure before any application exists.
CREATE TABLE IF NOT EXISTS deficiency (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID REFERENCES application(id) ON DELETE CASCADE,
    usid UUID NOT NULL REFERENCES scholar(usid) ON DELETE CASCADE,
    type VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    resolution_route TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'open',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT deficiency_status_known CHECK (status IN ('open', 'in_progress', 'resolved', 'waived'))
);
CREATE INDEX IF NOT EXISTS idx_deficiency_usid ON deficiency(usid);
CREATE INDEX IF NOT EXISTS idx_deficiency_status ON deficiency(status) WHERE status = 'open';
CREATE INDEX IF NOT EXISTS idx_deficiency_application ON deficiency(application_id)
    WHERE application_id IS NOT NULL;

-- 8. Disbursements — money in integer paise
CREATE TABLE IF NOT EXISTS disbursement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usid UUID NOT NULL REFERENCES scholar(usid) ON DELETE CASCADE,
    scheme VARCHAR(16) NOT NULL,
    sanctioned_amount_paise BIGINT NOT NULL DEFAULT 0,
    paid_amount_paise BIGINT NOT NULL DEFAULT 0,
    pfms_ref VARCHAR(64),
    failure_code VARCHAR(64),
    failure_reason TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    disbursed_at TIMESTAMPTZ,
    CONSTRAINT disbursement_amounts_non_negative
        CHECK (sanctioned_amount_paise >= 0 AND paid_amount_paise >= 0),
    CONSTRAINT disbursement_paid_within_sanctioned CHECK (paid_amount_paise <= sanctioned_amount_paise)
);
CREATE INDEX IF NOT EXISTS idx_disbursement_usid ON disbursement(usid);
-- "Why is my payment pending?" reads failed rows per USID.
CREATE INDEX IF NOT EXISTS idx_disbursement_failed ON disbursement(usid, failure_code)
    WHERE failure_code IS NOT NULL;

-- 9. DPDP consent artefacts
CREATE TABLE IF NOT EXISTS consent_artefact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usid UUID NOT NULL REFERENCES scholar(usid) ON DELETE CASCADE,
    purpose VARCHAR(128) NOT NULL,
    scope JSONB NOT NULL DEFAULT '[]'::jsonb,
    granted_by VARCHAR(64) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT consent_scope_is_array CHECK (jsonb_typeof(scope) = 'array'),
    CONSTRAINT consent_expiry_after_grant CHECK (expires_at IS NULL OR expires_at > granted_at)
);
CREATE INDEX IF NOT EXISTS idx_consent_usid ON consent_artefact(usid);
-- "Is there a live consent for this purpose?" is the hot lookup.
CREATE INDEX IF NOT EXISTS idx_consent_active ON consent_artefact(usid, purpose)
    WHERE revoked_at IS NULL;

-- 10. Access audit — APPEND-ONLY
-- usid deliberately does NOT cascade: under DPDP the access record must survive
-- the scholar it describes, so deleting a scholar is blocked until the audit is
-- lawfully purged.
CREATE TABLE IF NOT EXISTS access_audit (
    id BIGSERIAL PRIMARY KEY,
    usid UUID NOT NULL REFERENCES scholar(usid),
    accessor VARCHAR(128) NOT NULL,
    field_accessed VARCHAR(128) NOT NULL,
    consent_id UUID REFERENCES consent_artefact(id) ON DELETE SET NULL,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_audit_usid ON access_audit(usid);
CREATE INDEX IF NOT EXISTS idx_audit_time ON access_audit(accessed_at DESC);

-- 11. Versioned scheme rules (JSONB mirrors packages/rules/)
CREATE TABLE IF NOT EXISTS scheme_rule_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scheme VARCHAR(16) NOT NULL,
    academic_year VARCHAR(8) NOT NULL,
    rules_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rule_scheme_year UNIQUE (scheme, academic_year)
);

-- 12. Coverage-gap candidates (hashed keys only — no raw PII)
CREATE TABLE IF NOT EXISTS coverage_candidate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hashed_key VARCHAR(128) NOT NULL UNIQUE,
    state VARCHAR(64),
    district VARCHAR(64),
    block VARCHAR(64),
    school VARCHAR(256),
    class_level INT,
    gender VARCHAR(16),
    pvtg_status BOOLEAN NOT NULL DEFAULT FALSE,
    outreach_status VARCHAR(32) NOT NULL DEFAULT 'unreached',
    CONSTRAINT coverage_class_range CHECK (class_level IS NULL OR class_level BETWEEN 1 AND 12)
);
CREATE INDEX IF NOT EXISTS idx_coverage_geo ON coverage_candidate(state, district, block, school);
CREATE INDEX IF NOT EXISTS idx_coverage_key ON coverage_candidate(hashed_key);
CREATE INDEX IF NOT EXISTS idx_coverage_unreached ON coverage_candidate(district, school)
    WHERE outreach_status = 'unreached';

-- Append-only guards: reject UPDATE/DELETE/TRUNCATE on application_event and access_audit.
CREATE OR REPLACE FUNCTION forbid_append_only_write() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only: % forbidden', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_appevent_no_update ON application_event;
CREATE TRIGGER trg_appevent_no_update
    BEFORE UPDATE OR DELETE OR TRUNCATE ON application_event
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_append_only_write();

DROP TRIGGER IF EXISTS trg_audit_no_update ON access_audit;
CREATE TRIGGER trg_audit_no_update
    BEFORE UPDATE OR DELETE OR TRUNCATE ON access_audit
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_append_only_write();
