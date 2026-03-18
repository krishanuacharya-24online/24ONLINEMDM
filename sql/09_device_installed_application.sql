CREATE TABLE IF NOT EXISTS device_installed_application (
  id BIGSERIAL PRIMARY KEY,

  device_posture_payload_id BIGINT NOT NULL REFERENCES device_posture_payload(id) ON DELETE CASCADE,
  device_trust_profile_id BIGINT REFERENCES device_trust_profile(id) ON DELETE SET NULL,
  capture_time TIMESTAMPTZ NOT NULL DEFAULT now(),

  app_name TEXT NOT NULL,
  publisher TEXT,
  package_id TEXT,
  app_os_type TEXT NOT NULL CHECK (app_os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX')),
  app_version TEXT,
  latest_available_version TEXT,
  is_system_app BOOLEAN,
  install_source TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED', 'UNKNOWN')),

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'posture-parser'
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_installed_app_payload_identity
  ON device_installed_application (
    device_posture_payload_id,
    app_os_type,
    COALESCE(package_id, ''),
    lower(app_name)
  );

CREATE INDEX IF NOT EXISTS idx_device_installed_app_profile_time
  ON device_installed_application (device_trust_profile_id, capture_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_installed_app_lookup
  ON device_installed_application (app_os_type, COALESCE(package_id, ''), lower(app_name));
