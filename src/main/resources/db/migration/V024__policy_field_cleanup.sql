ALTER TABLE system_information_rule
  DROP COLUMN IF EXISTS os_version,
  DROP COLUMN IF EXISTS time_zone,
  DROP COLUMN IF EXISTS kernel_version,
  DROP COLUMN IF EXISTS apiLevel,
  DROP COLUMN IF EXISTS osBuildNumber,
  DROP COLUMN IF EXISTS manufacturer,
  DROP COLUMN IF EXISTS rootDetected,
  DROP COLUMN IF EXISTS runningOnEmulator,
  DROP COLUMN IF EXISTS usb_debigging_status;

DROP INDEX IF EXISTS idx_reject_os_category;
DROP INDEX IF EXISTS idx_reject_policy;

ALTER TABLE reject_application_list
  DROP COLUMN IF EXISTS threat_type,
  DROP COLUMN IF EXISTS blocked_reason,
  DROP COLUMN IF EXISTS app_category,
  DROP COLUMN IF EXISTS app_latest_version,
  DROP COLUMN IF EXISTS latest_ver_major,
  DROP COLUMN IF EXISTS latest_ver_minor,
  DROP COLUMN IF EXISTS latest_ver_patch,
  DROP COLUMN IF EXISTS min_ver_major,
  DROP COLUMN IF EXISTS min_ver_minor,
  DROP COLUMN IF EXISTS min_ver_patch;

CREATE INDEX IF NOT EXISTS idx_reject_policy
  ON reject_application_list (policy_tag, severity);
