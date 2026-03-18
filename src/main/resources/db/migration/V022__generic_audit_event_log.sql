-- Generic append-only audit event stream for cross-domain observability.

CREATE TABLE IF NOT EXISTS audit_event_log (
    id BIGSERIAL PRIMARY KEY,
    event_id TEXT NOT NULL UNIQUE,
    event_category TEXT NOT NULL,
    event_type TEXT NOT NULL,
    action TEXT,
    tenant_id TEXT,
    actor TEXT NOT NULL,
    entity_type TEXT,
    entity_id TEXT,
    status TEXT NOT NULL,
    metadata_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_event_log_created_at
    ON audit_event_log (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_event_log_category_type
    ON audit_event_log (event_category, event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_event_log_tenant
    ON audit_event_log (COALESCE(tenant_id, ''), created_at DESC);

