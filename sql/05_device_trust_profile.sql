CREATE TABLE IF NOT EXISTS device_trust_profile (
  id BIGSERIAL PRIMARY KEY,

  tenant_id TEXT,
  device_external_id TEXT NOT NULL,
  device_type TEXT CHECK (device_type IN ('PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER')),
  os_type TEXT NOT NULL CHECK (os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD')),
  os_name TEXT,
  os_lifecycle_state TEXT NOT NULL DEFAULT 'NOT_TRACKED',

  current_score SMALLINT NOT NULL DEFAULT 100 CHECK (current_score BETWEEN 0 AND 100),
  score_band TEXT NOT NULL DEFAULT 'TRUSTED' CHECK (score_band IN ('CRITICAL', 'HIGH_RISK', 'MEDIUM_RISK', 'LOW_RISK', 'TRUSTED')),
  posture_status TEXT NOT NULL DEFAULT 'COMPLIANT' CHECK (posture_status IN ('COMPLIANT', 'NON_COMPLIANT', 'UNKNOWN')),

  last_event_at TIMESTAMPTZ,
  last_recalculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL,

  CONSTRAINT ck_device_profile_os_name_linux
    CHECK (os_name IS NULL OR os_type = 'LINUX'),
  CONSTRAINT ck_device_profile_os_lifecycle_state
    CHECK (os_lifecycle_state IN ('SUPPORTED', 'EOL', 'EEOL', 'NOT_TRACKED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_trust_profile_device
  ON device_trust_profile (COALESCE(tenant_id, ''), device_external_id)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_device_trust_profile_score
  ON device_trust_profile (score_band, current_score);

CREATE INDEX IF NOT EXISTS idx_device_trust_profile_target
  ON device_trust_profile (os_type, os_name, device_type);

CREATE INDEX IF NOT EXISTS idx_device_trust_profile_lifecycle
  ON device_trust_profile (os_lifecycle_state, current_score);
