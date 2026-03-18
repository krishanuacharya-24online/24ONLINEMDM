CREATE TABLE IF NOT EXISTS tenant_api_key (
    id              BIGSERIAL PRIMARY KEY,
    tenant_master_id BIGINT NOT NULL REFERENCES tenant_master(id) ON DELETE CASCADE,
    key_hash        TEXT NOT NULL,
    key_hint        TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      TEXT NOT NULL DEFAULT 'system',
    revoked_at      TIMESTAMPTZ,
    revoked_by      TEXT,
    CONSTRAINT ck_tenant_api_key_revocation
        CHECK (
            (status = 'ACTIVE' AND revoked_at IS NULL)
            OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_api_key_active_per_tenant
    ON tenant_api_key (tenant_master_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_tenant_api_key_tenant_created
    ON tenant_api_key (tenant_master_id, created_at DESC);
