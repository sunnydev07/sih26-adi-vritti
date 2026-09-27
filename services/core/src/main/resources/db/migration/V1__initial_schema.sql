-- V1__initial_schema.sql — Adi-Vritti core data model (12 tables).
-- Conventions: money BIGINT paise, TIMESTAMPTZ dates, Aadhaar as ref keys only.
-- FORWARD-ONLY: never edit this file; add V2+ migrations instead.

-- 1. Scholar (USID identity)
CREATE TABLE scholar (
    usid UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    demographics JSONB NOT NULL DEFAULT '{}',
    guardian_usid UUID REFERENCES scholar(usid),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Links USID -> external system IDs (NSP/SFMP/NOS/UDISE/APAAR)
CREATE TABLE scholar_system_link (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usid UUID NOT NULL REFERENCES scholar(usid),
    system_name VARCHAR(16) NOT NULL,
    external_id VARCHAR(64) NOT NULL,
    match_confidence DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    resolution_method VARCHAR(32) NOT NULL DEFAULT 'deterministic',
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (system_name, external_id)
);
CREATE INDEX idx_syslink_usid ON scholar_system_link(usid);

-- 3. Verified Claims Wallet
CREATE TABLE claim (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usid UUID NOT NULL REFERENCES scholar(usid),
    claim_type VARCHAR(32) NOT NULL,
    value_encrypted BYTEA NOT NULL,
    source VARCHAR(64) NOT NULL,
    method VARCHAR(32) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until TIMESTAMPTZ NOT NULL,
    evidence_ref VARCHAR(512),
    verifier VARCHAR(128)
);
CREATE INDEX idx_claim_usid ON claim(usid);
CREATE INDEX idx_claim_type_valid ON claim(claim_type, valid_until);

-- 4. Signed claim attestations (Ed25519 JWS)
CREATE TABLE claim_attestation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    claim_id UUID NOT NULL REFERENCES claim(id),
    jws_token TEXT NOT NULL,
    public_key_id VARCHAR(128) NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Application stage machine
CREATE TABLE application (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usid UUID NOT NULL REFERENCES scholar(usid),
    scheme VARCHAR(16) NOT NULL,
    academic_year VARCHAR(8) NOT NULL,
    stage VARCHAR(32) NOT NULL DEFAULT 'submitted',
    current_actor VARCHAR(64) NOT NULL DEFAULT 'institute',
    sla_deadline TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_application_usid ON application(usid);
CREATE INDEX idx_application_scheme_year ON application(scheme, academic_year);
CREATE INDEX idx_application_stage ON application(stage);

-- 6. Application events — APPEND-ONLY
CREATE TABLE application_event (
    id BIGSERIAL PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES application(id),
    stage VARCHAR(32) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes TEXT
);
CREATE INDEX idx_appevent_app ON application_event(application_id);

-- 7. Deficiencies (verification failure -> actionable fix, never a block)
CREATE TABLE deficiency (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id UUID REFERENCES application(id),
    usid UUID NOT NULL REFERENCES scholar(usid),
    type VARCHAR(64) NOT NULL,
    message TEXT NOT NULL,
    resolution_route TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'open',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_deficiency_usid ON deficiency(usid);

-- 8. Disbursements — money in integer paise
CREATE TABLE disbursement (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usid UUID NOT NULL REFERENCES scholar(usid),
    scheme VARCHAR(16) NOT NULL,
    sanctioned_amount_paise BIGINT NOT NULL DEFAULT 0,
    paid_amount_paise BIGINT NOT NULL DEFAULT 0,
    pfms_ref VARCHAR(64),
    failure_code VARCHAR(64),
    failure_reason TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    disbursed_at TIMESTAMPTZ
);
CREATE INDEX idx_disbursement_usid ON disbursement(usid);

-- 9. DPDP consent artefacts
CREATE TABLE consent_artefact (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usid UUID NOT NULL REFERENCES scholar(usid),
    purpose VARCHAR(128) NOT NULL,
    scope JSONB NOT NULL DEFAULT '[]',
    granted_by VARCHAR(64) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_consent_usid ON consent_artefact(usid);

-- 10. Access audit — APPEND-ONLY
CREATE TABLE access_audit (
    id BIGSERIAL PRIMARY KEY,
    usid UUID NOT NULL REFERENCES scholar(usid),
    accessor VARCHAR(128) NOT NULL,
    field_accessed VARCHAR(128) NOT NULL,
    consent_id UUID REFERENCES consent_artefact(id),
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_usid ON access_audit(usid);

-- 11. Versioned scheme rules (JSONB mirrors packages/rules/)
CREATE TABLE scheme_rule_version (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    scheme VARCHAR(16) NOT NULL,
    academic_year VARCHAR(8) NOT NULL,
    rules_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (scheme, academic_year)
);

-- 12. Coverage-gap candidates (hashed keys only — no raw PII)
CREATE TABLE coverage_candidate (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hashed_key VARCHAR(128) NOT NULL UNIQUE,
    state VARCHAR(64),
    district VARCHAR(64),
    block VARCHAR(64),
    school VARCHAR(256),
    class_level INT,
    gender VARCHAR(16),
    pvtg_status BOOLEAN NOT NULL DEFAULT FALSE,
    outreach_status VARCHAR(32) NOT NULL DEFAULT 'unreached'
);
CREATE INDEX idx_coverage_geo ON coverage_candidate(state, district, block, school);
CREATE INDEX idx_coverage_key ON coverage_candidate(hashed_key);

-- Append-only guards: reject UPDATE/DELETE on application_event and access_audit
CREATE OR REPLACE FUNCTION forbid_append_only_write() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only: UPDATE/DELETE forbidden', TG_TABLE_NAME;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_appevent_no_update BEFORE UPDATE OR DELETE ON application_event
    FOR EACH ROW EXECUTE FUNCTION forbid_append_only_write();
CREATE TRIGGER trg_audit_no_update BEFORE UPDATE OR DELETE ON access_audit
    FOR EACH ROW EXECUTE FUNCTION forbid_append_only_write();
