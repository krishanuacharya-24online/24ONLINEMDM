CREATE TABLE IF NOT EXISTS tenant_master (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    TEXT NOT NULL UNIQUE,
    name         TEXT NOT NULL,
    status       TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   TEXT NOT NULL DEFAULT 'system',
    modified_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_by  TEXT NOT NULL DEFAULT 'system',
    is_deleted   BOOLEAN NOT NULL DEFAULT FALSE
);

