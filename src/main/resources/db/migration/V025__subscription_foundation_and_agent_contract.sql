CREATE TABLE IF NOT EXISTS subscription_plan (
  id BIGSERIAL PRIMARY KEY,
  plan_code TEXT NOT NULL,
  plan_name TEXT NOT NULL,
  description TEXT,
  max_active_devices INTEGER NOT NULL CHECK (max_active_devices >= 1),
  max_tenant_users INTEGER NOT NULL CHECK (max_tenant_users >= 1),
  max_monthly_payloads BIGINT NOT NULL CHECK (max_monthly_payloads >= 1),
  data_retention_days INTEGER NOT NULL CHECK (data_retention_days >= 1),
  premium_reporting_enabled BOOLEAN NOT NULL DEFAULT false,
  advanced_controls_enabled BOOLEAN NOT NULL DEFAULT false,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  is_deleted BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'system-seed',
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL DEFAULT 'system-seed'
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_plan_code
  ON subscription_plan (plan_code)
  WHERE is_deleted = false;

CREATE TABLE IF NOT EXISTS tenant_subscription (
  id BIGSERIAL PRIMARY KEY,
  tenant_master_id BIGINT NOT NULL REFERENCES tenant_master(id) ON DELETE CASCADE,
  subscription_plan_id BIGINT NOT NULL REFERENCES subscription_plan(id),
  subscription_state TEXT NOT NULL CHECK (
    subscription_state IN ('TRIALING', 'ACTIVE', 'GRACE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED', 'EXPIRED')
  ),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  current_period_start TIMESTAMPTZ NOT NULL DEFAULT now(),
  current_period_end TIMESTAMPTZ,
  grace_ends_at TIMESTAMPTZ,
  suspended_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'system-seed',
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL DEFAULT 'system-seed',
  CONSTRAINT ck_tenant_subscription_period_window
    CHECK (current_period_end IS NULL OR current_period_end > current_period_start),
  CONSTRAINT ck_tenant_subscription_grace_window
    CHECK (grace_ends_at IS NULL OR current_period_end IS NULL OR grace_ends_at >= current_period_end)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_subscription_tenant
  ON tenant_subscription (tenant_master_id);

CREATE INDEX IF NOT EXISTS idx_tenant_subscription_state
  ON tenant_subscription (subscription_state, tenant_master_id);

CREATE TABLE IF NOT EXISTS tenant_usage_snapshot (
  id BIGSERIAL PRIMARY KEY,
  tenant_master_id BIGINT NOT NULL REFERENCES tenant_master(id) ON DELETE CASCADE,
  usage_month DATE NOT NULL,
  active_device_count INTEGER NOT NULL DEFAULT 0 CHECK (active_device_count >= 0),
  active_user_count INTEGER NOT NULL DEFAULT 0 CHECK (active_user_count >= 0),
  posture_payload_count BIGINT NOT NULL DEFAULT 0 CHECK (posture_payload_count >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'system-seed',
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL DEFAULT 'system-seed'
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_usage_snapshot_month
  ON tenant_usage_snapshot (tenant_master_id, usage_month);

CREATE INDEX IF NOT EXISTS idx_tenant_usage_snapshot_lookup
  ON tenant_usage_snapshot (usage_month, tenant_master_id);

CREATE TABLE IF NOT EXISTS tenant_feature_override (
  id BIGSERIAL PRIMARY KEY,
  tenant_master_id BIGINT NOT NULL REFERENCES tenant_master(id) ON DELETE CASCADE,
  feature_key TEXT NOT NULL,
  enabled BOOLEAN NOT NULL,
  expires_at TIMESTAMPTZ,
  reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'system-seed',
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL DEFAULT 'system-seed'
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_feature_override
  ON tenant_feature_override (tenant_master_id, feature_key);

CREATE INDEX IF NOT EXISTS idx_tenant_feature_override_lookup
  ON tenant_feature_override (feature_key, tenant_master_id, expires_at);

INSERT INTO subscription_plan (
  plan_code,
  plan_name,
  description,
  max_active_devices,
  max_tenant_users,
  max_monthly_payloads,
  data_retention_days,
  premium_reporting_enabled,
  advanced_controls_enabled,
  status,
  created_by,
  modified_by
)
VALUES
  (
    'TRIAL',
    'Trial',
    'Starter trial plan for new tenants',
    25,
    10,
    5000,
    30,
    false,
    false,
    'ACTIVE',
    'system-seed',
    'system-seed'
  ),
  (
    'STANDARD',
    'Standard',
    'Standard production plan',
    250,
    100,
    100000,
    90,
    true,
    false,
    'ACTIVE',
    'system-seed',
    'system-seed'
  ),
  (
    'ENTERPRISE',
    'Enterprise',
    'Enterprise plan with expanded limits and controls',
    5000,
    1000,
    5000000,
    365,
    true,
    true,
    'ACTIVE',
    'system-seed',
    'system-seed'
  )
ON CONFLICT DO NOTHING;

WITH trial_plan AS (
  SELECT id
  FROM subscription_plan
  WHERE plan_code = 'TRIAL'
    AND is_deleted = false
  LIMIT 1
)
INSERT INTO tenant_subscription (
  tenant_master_id,
  subscription_plan_id,
  subscription_state,
  started_at,
  current_period_start,
  current_period_end,
  grace_ends_at,
  notes,
  created_at,
  created_by,
  modified_at,
  modified_by
)
SELECT
  tenant.id,
  trial_plan.id,
  'TRIALING',
  now(),
  now(),
  now() + INTERVAL '30 days',
  now() + INTERVAL '37 days',
  'Auto-provisioned trial subscription',
  now(),
  'system-backfill',
  now(),
  'system-backfill'
FROM tenant_master tenant
CROSS JOIN trial_plan
WHERE tenant.is_deleted = false
  AND NOT EXISTS (
    SELECT 1
    FROM tenant_subscription existing
    WHERE existing.tenant_master_id = tenant.id
  );

INSERT INTO tenant_usage_snapshot (
  tenant_master_id,
  usage_month,
  active_device_count,
  active_user_count,
  posture_payload_count,
  created_at,
  created_by,
  modified_at,
  modified_by
)
SELECT
  tenant.id,
  date_trunc('month', now())::date,
  COALESCE((
    SELECT COUNT(*)
    FROM device_enrollment enrollment
    WHERE enrollment.tenant_id = tenant.tenant_id
      AND enrollment.status = 'ACTIVE'
  ), 0),
  COALESCE((
    SELECT COUNT(*)
    FROM auth_user auth_user_row
    WHERE auth_user_row.tenant_id = tenant.id
      AND auth_user_row.is_deleted = false
      AND auth_user_row.status = 'ACTIVE'
  ), 0),
  COALESCE((
    SELECT COUNT(*)
    FROM device_posture_payload payload
    WHERE payload.tenant_id = tenant.tenant_id
      AND payload.received_at >= date_trunc('month', now())
      AND payload.received_at < date_trunc('month', now()) + INTERVAL '1 month'
  ), 0),
  now(),
  'system-backfill',
  now(),
  'system-backfill'
FROM tenant_master tenant
WHERE tenant.is_deleted = false
ON CONFLICT (tenant_master_id, usage_month) DO UPDATE
SET
  active_device_count = EXCLUDED.active_device_count,
  active_user_count = EXCLUDED.active_user_count,
  posture_payload_count = EXCLUDED.posture_payload_count,
  modified_at = now(),
  modified_by = 'system-backfill';

INSERT INTO lkp_master (lookup_type, code, description)
VALUES
  ('lkp_subscription_state', 'TRIALING', 'Tenant is within a trial period'),
  ('lkp_subscription_state', 'ACTIVE', 'Tenant subscription is active'),
  ('lkp_subscription_state', 'GRACE', 'Tenant is within a grace period'),
  ('lkp_subscription_state', 'PAST_DUE', 'Tenant billing is past due'),
  ('lkp_subscription_state', 'SUSPENDED', 'Tenant subscription is suspended'),
  ('lkp_subscription_state', 'CANCELLED', 'Tenant subscription is cancelled'),
  ('lkp_subscription_state', 'EXPIRED', 'Tenant subscription has expired'),
  ('lkp_feature_key', 'PREMIUM_REPORTING', 'Premium reporting features'),
  ('lkp_feature_key', 'ADVANCED_CONTROLS', 'Advanced product controls')
ON CONFLICT (lookup_type, code) DO UPDATE
SET description = EXCLUDED.description;

ALTER TABLE device_posture_payload
  ADD COLUMN IF NOT EXISTS capture_time TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS agent_version TEXT,
  ADD COLUMN IF NOT EXISTS agent_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS schema_compatibility_status TEXT NOT NULL DEFAULT 'SUPPORTED',
  ADD COLUMN IF NOT EXISTS validation_warnings JSONB NOT NULL DEFAULT '[]'::jsonb;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    WHERE c.conname = 'ck_device_posture_payload_schema_compatibility_status'
      AND c.conrelid = to_regclass('device_posture_payload')
  ) THEN
    ALTER TABLE device_posture_payload
      ADD CONSTRAINT ck_device_posture_payload_schema_compatibility_status
      CHECK (schema_compatibility_status IN ('SUPPORTED', 'SUPPORTED_WITH_WARNINGS', 'UNVERIFIED'));
  END IF;
END
$$;

UPDATE device_posture_payload
SET capture_time = COALESCE(capture_time, received_at)
WHERE capture_time IS NULL;

CREATE INDEX IF NOT EXISTS idx_posture_payload_capture_time
  ON device_posture_payload (capture_time DESC);
