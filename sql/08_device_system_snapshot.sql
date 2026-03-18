CREATE TABLE IF NOT EXISTS device_system_snapshot (
  id BIGSERIAL PRIMARY KEY,

  device_posture_payload_id BIGINT NOT NULL REFERENCES device_posture_payload(id) ON DELETE CASCADE,
  device_trust_profile_id BIGINT REFERENCES device_trust_profile(id) ON DELETE SET NULL,

  capture_time TIMESTAMPTZ NOT NULL DEFAULT now(),
  device_type TEXT CHECK (device_type IN ('PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER')),
  os_type TEXT NOT NULL CHECK (os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD')),
  os_name TEXT,
  os_cycle TEXT,
  os_release_lifecycle_master_id BIGINT,
  os_version TEXT,
  time_zone TEXT,
  kernel_version TEXT,
  api_level INTEGER CHECK (api_level IS NULL OR api_level >= 0),
  os_build_number TEXT,
  manufacturer TEXT,

  root_detected BOOLEAN,
  running_on_emulator BOOLEAN,
  usb_debugging_status BOOLEAN,
  is_latest BOOLEAN NOT NULL DEFAULT true,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'posture-parser',

  CONSTRAINT ck_snapshot_os_name_linux
    CHECK (os_name IS NULL OR os_type = 'LINUX')
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_system_snapshot_payload
  ON device_system_snapshot (device_posture_payload_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_system_snapshot_latest
  ON device_system_snapshot (device_trust_profile_id)
  WHERE is_latest = true AND device_trust_profile_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_device_system_snapshot_profile_time
  ON device_system_snapshot (device_trust_profile_id, capture_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_system_snapshot_targeting
  ON device_system_snapshot (os_type, os_name, os_cycle, device_type, capture_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_system_snapshot_lifecycle
  ON device_system_snapshot (os_release_lifecycle_master_id, capture_time DESC);
