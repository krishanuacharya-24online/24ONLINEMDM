-- Append-only audit trail for policy create/update/delete/clone operations.

CREATE TABLE IF NOT EXISTS policy_change_audit (
    id BIGSERIAL PRIMARY KEY,
    policy_type TEXT NOT NULL,
    policy_id BIGINT,
    operation TEXT NOT NULL
        CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE', 'CLONE')),
    tenant_id TEXT,
    actor TEXT NOT NULL,
    approval_ticket TEXT,
    before_state_json JSONB,
    after_state_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_policy_change_audit_policy
    ON policy_change_audit (policy_type, policy_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_policy_change_audit_tenant
    ON policy_change_audit (COALESCE(tenant_id, ''), created_at DESC);

