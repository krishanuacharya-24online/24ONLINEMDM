CREATE TABLE IF NOT EXISTS device_setup_key (
    id BIGSERIAL PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    key_hash TEXT NOT NULL,
    key_hint TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    max_uses INTEGER NOT NULL CHECK (max_uses > 0),
    used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by TEXT NOT NULL DEFAULT 'system',
    modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_by TEXT NOT NULL DEFAULT 'system',
    CONSTRAINT ck_device_setup_key_uses
        CHECK (used_count <= max_uses)
);

CREATE INDEX IF NOT EXISTS idx_device_setup_key_tenant_status
    ON device_setup_key (COALESCE(tenant_id, ''), status, expires_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_setup_key_hash
    ON device_setup_key (COALESCE(tenant_id, ''), key_hash);

CREATE TABLE IF NOT EXISTS device_qr_enroll_session (
    id BIGSERIAL PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    challenge_hash TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CLAIMED', 'EXPIRED', 'CANCELLED')),
    expires_at TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by TEXT NOT NULL DEFAULT 'system',
    modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_by TEXT NOT NULL DEFAULT 'system',
    CONSTRAINT ck_device_qr_session_claimed_time
        CHECK (claimed_at IS NULL OR claimed_at >= created_at)
);

CREATE INDEX IF NOT EXISTS idx_device_qr_session_tenant_status
    ON device_qr_enroll_session (COALESCE(tenant_id, ''), status, expires_at DESC);

CREATE TABLE IF NOT EXISTS device_enrollment (
    id BIGSERIAL PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    enrollment_no TEXT NOT NULL,
    enrollment_method TEXT NOT NULL
        CHECK (enrollment_method IN ('SETUP_KEY', 'QR')),
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DE_ENROLLED', 'EXPIRED')),
    agent_id TEXT,
    device_label TEXT,
    device_fingerprint TEXT,
    setup_key_id BIGINT REFERENCES device_setup_key(id) ON DELETE SET NULL,
    qr_session_id BIGINT REFERENCES device_qr_enroll_session(id) ON DELETE SET NULL,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    de_enrolled_at TIMESTAMPTZ,
    de_enroll_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by TEXT NOT NULL DEFAULT 'system',
    modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_by TEXT NOT NULL DEFAULT 'system',
    CONSTRAINT ck_device_enrollment_de_status
        CHECK (
            (status = 'DE_ENROLLED' AND de_enrolled_at IS NOT NULL)
            OR (status <> 'DE_ENROLLED')
        )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_enrollment_tenant_no
    ON device_enrollment (COALESCE(tenant_id, ''), enrollment_no);

CREATE INDEX IF NOT EXISTS idx_device_enrollment_tenant_status
    ON device_enrollment (COALESCE(tenant_id, ''), status, enrolled_at DESC);

CREATE TABLE IF NOT EXISTS device_agent_credential (
    id BIGSERIAL PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    device_enrollment_id BIGINT NOT NULL REFERENCES device_enrollment(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    token_hint TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by TEXT NOT NULL DEFAULT 'system',
    revoked_at TIMESTAMPTZ,
    revoked_by TEXT,
    CONSTRAINT ck_device_credential_revoked_time
        CHECK (revoked_at IS NULL OR revoked_at >= created_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_agent_credential_active
    ON device_agent_credential (device_enrollment_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_device_agent_credential_tenant_status
    ON device_agent_credential (COALESCE(tenant_id, ''), status, created_at DESC);
