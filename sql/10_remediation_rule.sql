CREATE TABLE IF NOT EXISTS remediation_rule (
  id BIGSERIAL PRIMARY KEY,

  remediation_code TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  remediation_type TEXT NOT NULL
    CHECK (remediation_type IN ('USER_ACTION', 'AUTO_ACTION', 'NETWORK_RESTRICT', 'APP_REMOVAL', 'OS_UPDATE', 'POLICY_ACK')),

  os_type TEXT CHECK (os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX')),
  device_type TEXT CHECK (device_type IN ('PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER')),
  instruction_json JSONB NOT NULL DEFAULT '{}'::jsonb,

  priority SMALLINT NOT NULL DEFAULT 100 CHECK (priority BETWEEN 1 AND 1000),
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  effective_to TIMESTAMPTZ,
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL,

  CONSTRAINT ck_remediation_effective_window
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_remediation_code
  ON remediation_rule (remediation_code)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_remediation_targeting
  ON remediation_rule (os_type, device_type, status, priority)
  WHERE is_deleted = false;
