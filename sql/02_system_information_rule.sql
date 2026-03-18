CREATE TABLE IF NOT EXISTS system_information_rule (
  id BIGSERIAL PRIMARY KEY,

  rule_code TEXT NOT NULL,

  priority INTEGER NOT NULL DEFAULT 100 CHECK (priority BETWEEN 1 AND 100000),
  version INTEGER NOT NULL DEFAULT 1 CHECK (version BETWEEN 1 AND 100000),

  match_mode TEXT NOT NULL CHECK (match_mode IN ('ALL', 'ANY')),
  compliance_action TEXT NOT NULL CHECK (compliance_action IN ('ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY')),
  risk_score_delta SMALLINT NOT NULL DEFAULT 0 CHECK (risk_score_delta BETWEEN -1000 AND 1000),

  rule_tag TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  severity SMALLINT NOT NULL CHECK (severity BETWEEN 1 AND 5),
  description TEXT,

  device_type TEXT CHECK (device_type IN ('PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER')),
  os_type TEXT NOT NULL CHECK (os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD')),
  os_name TEXT,
  os_version TEXT,
  time_zone TEXT,
  kernel_version TEXT,
  apiLevel INTEGER CHECK (apiLevel IS NULL OR apiLevel >= 0),
  osBuildNumber TEXT,
  manufacturer TEXT,

  rootDetected BOOLEAN,
  runningOnEmulator BOOLEAN,
  usb_debigging_status BOOLEAN,

  effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  effective_to TIMESTAMPTZ,
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL,

  CONSTRAINT ck_sysrule_os_name_linux
    CHECK (os_name IS NULL OR os_type = 'LINUX'),
  CONSTRAINT ck_sysrule_effective_window
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sys_rule_code
  ON system_information_rule (rule_code)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sysrule_tag_ver
  ON system_information_rule (os_type, os_name, device_type, rule_tag, version)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sysrule_eval_active
  ON system_information_rule (os_type, os_name, device_type, priority)
  WHERE status = 'ACTIVE' AND is_deleted = false;
