-- Master lifecycle catalog used for OS support posture decisions (EOL/EEOL aware).

CREATE TABLE IF NOT EXISTS os_release_lifecycle_master (
  id BIGSERIAL PRIMARY KEY,

  platform_code TEXT NOT NULL,
  os_type TEXT,
  os_name TEXT,
  cycle TEXT NOT NULL,

  released_on DATE,
  eol_on DATE,
  eeol_on DATE,
  latest_version TEXT,

  support_state TEXT NOT NULL DEFAULT 'TRACKED'
    CHECK (support_state IN ('TRACKED', 'SUPPORTED', 'NOT_FOUND')),
  source_name TEXT NOT NULL DEFAULT 'endoflife.date',
  source_url TEXT,
  notes TEXT,

  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'system-seed',
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL DEFAULT 'system-seed',

  CONSTRAINT uq_os_release_lifecycle UNIQUE (platform_code, cycle),
  CONSTRAINT ck_os_lifecycle_cycle_nonblank CHECK (length(trim(cycle)) > 0),
  CONSTRAINT ck_os_lifecycle_linux_name CHECK (os_name IS NULL OR os_type = 'LINUX'),
  CONSTRAINT ck_os_lifecycle_eol_vs_release CHECK (eol_on IS NULL OR released_on IS NULL OR eol_on >= released_on),
  CONSTRAINT ck_os_lifecycle_eeol_requires_eol CHECK (eeol_on IS NULL OR eol_on IS NOT NULL),
  CONSTRAINT ck_os_lifecycle_eeol_order CHECK (eeol_on IS NULL OR eol_on IS NULL OR eeol_on >= eol_on)
);

CREATE INDEX IF NOT EXISTS idx_os_lifecycle_master_lookup
  ON os_release_lifecycle_master (platform_code, support_state, eol_on, eeol_on);

CREATE INDEX IF NOT EXISTS idx_os_lifecycle_master_mapping
  ON os_release_lifecycle_master (os_type, os_name, cycle);

-- Add lifecycle linkage columns to runtime tables (migration-safe).
ALTER TABLE device_system_snapshot
  ADD COLUMN IF NOT EXISTS os_cycle TEXT,
  ADD COLUMN IF NOT EXISTS os_release_lifecycle_master_id BIGINT;

ALTER TABLE device_trust_profile
  ADD COLUMN IF NOT EXISTS os_release_lifecycle_master_id BIGINT,
  ADD COLUMN IF NOT EXISTS os_lifecycle_state TEXT;

ALTER TABLE posture_evaluation_run
  ADD COLUMN IF NOT EXISTS os_release_lifecycle_master_id BIGINT,
  ADD COLUMN IF NOT EXISTS os_lifecycle_state TEXT;

ALTER TABLE posture_evaluation_match
  ADD COLUMN IF NOT EXISTS os_release_lifecycle_master_id BIGINT,
  ADD COLUMN IF NOT EXISTS os_lifecycle_state TEXT;

ALTER TABLE device_trust_score_event
  ADD COLUMN IF NOT EXISTS os_release_lifecycle_master_id BIGINT,
  ADD COLUMN IF NOT EXISTS os_lifecycle_state TEXT;

-- Expand OS family checks for existing databases where old inline checks were created.
ALTER TABLE reject_application_list
  DROP CONSTRAINT IF EXISTS reject_application_list_app_os_type_check;

ALTER TABLE system_information_rule
  DROP CONSTRAINT IF EXISTS system_information_rule_os_type_check;

ALTER TABLE device_trust_profile
  DROP CONSTRAINT IF EXISTS device_trust_profile_os_type_check;

ALTER TABLE device_system_snapshot
  DROP CONSTRAINT IF EXISTS device_system_snapshot_os_type_check;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    WHERE c.conname = 'ck_reject_app_os_type'
      AND c.conrelid = 'reject_application_list'::regclass
  ) THEN
    ALTER TABLE reject_application_list
      ADD CONSTRAINT ck_reject_app_os_type
      CHECK (app_os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'));
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    WHERE c.conname = 'ck_system_rule_os_type'
      AND c.conrelid = 'system_information_rule'::regclass
  ) THEN
    ALTER TABLE system_information_rule
      ADD CONSTRAINT ck_system_rule_os_type
      CHECK (os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'));
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    WHERE c.conname = 'ck_device_profile_os_type'
      AND c.conrelid = 'device_trust_profile'::regclass
  ) THEN
    ALTER TABLE device_trust_profile
      ADD CONSTRAINT ck_device_profile_os_type
      CHECK (os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'));
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint c
    WHERE c.conname = 'ck_snapshot_os_type'
      AND c.conrelid = 'device_system_snapshot'::regclass
  ) THEN
    ALTER TABLE device_system_snapshot
      ADD CONSTRAINT ck_snapshot_os_type
      CHECK (os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'));
  END IF;
END
$$;

-- Ensure lifecycle-state checks exist where FK may not have been applied yet.
DO $$
DECLARE
  rec RECORD;
BEGIN
  FOR rec IN
    SELECT *
    FROM (
      VALUES
        ('device_trust_profile', 'ck_device_profile_os_lifecycle_state',
         'CHECK (os_lifecycle_state IS NULL OR os_lifecycle_state IN (''SUPPORTED'', ''EOL'', ''EEOL'', ''NOT_TRACKED''))'),
        ('posture_evaluation_run', 'ck_eval_run_os_lifecycle_state',
         'CHECK (os_lifecycle_state IS NULL OR os_lifecycle_state IN (''SUPPORTED'', ''EOL'', ''EEOL'', ''NOT_TRACKED''))'),
        ('posture_evaluation_match', 'ck_eval_match_os_lifecycle_state',
         'CHECK (os_lifecycle_state IS NULL OR os_lifecycle_state IN (''SUPPORTED'', ''EOL'', ''EEOL'', ''NOT_TRACKED''))'),
        ('device_trust_score_event', 'ck_trust_event_os_lifecycle_state',
         'CHECK (os_lifecycle_state IS NULL OR os_lifecycle_state IN (''SUPPORTED'', ''EOL'', ''EEOL'', ''NOT_TRACKED''))')
    ) AS v(table_name, constraint_name, constraint_sql)
  LOOP
    IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint c
      WHERE c.conname = rec.constraint_name
        AND c.conrelid = to_regclass(rec.table_name)
    ) THEN
      EXECUTE format(
        'ALTER TABLE %I ADD CONSTRAINT %I %s',
        rec.table_name,
        rec.constraint_name,
        rec.constraint_sql
      );
    END IF;
  END LOOP;
END
$$;

-- Add lifecycle FKs (migration-safe).
DO $$
DECLARE
  fk RECORD;
BEGIN
  FOR fk IN
    SELECT *
    FROM (
      VALUES
        ('device_system_snapshot', 'fk_snapshot_os_lifecycle_master', 'FOREIGN KEY (os_release_lifecycle_master_id) REFERENCES os_release_lifecycle_master(id)'),
        ('device_trust_profile', 'fk_device_profile_os_lifecycle_master', 'FOREIGN KEY (os_release_lifecycle_master_id) REFERENCES os_release_lifecycle_master(id)'),
        ('posture_evaluation_run', 'fk_eval_run_os_lifecycle_master', 'FOREIGN KEY (os_release_lifecycle_master_id) REFERENCES os_release_lifecycle_master(id)'),
        ('posture_evaluation_match', 'fk_eval_match_os_lifecycle_master', 'FOREIGN KEY (os_release_lifecycle_master_id) REFERENCES os_release_lifecycle_master(id)'),
        ('device_trust_score_event', 'fk_trust_event_os_lifecycle_master', 'FOREIGN KEY (os_release_lifecycle_master_id) REFERENCES os_release_lifecycle_master(id)')
    ) AS v(table_name, constraint_name, constraint_sql)
  LOOP
    IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint c
      WHERE c.conname = fk.constraint_name
        AND c.conrelid = to_regclass(fk.table_name)
    ) THEN
      EXECUTE format(
        'ALTER TABLE %I ADD CONSTRAINT %I %s',
        fk.table_name,
        fk.constraint_name,
        fk.constraint_sql
      );
    END IF;
  END LOOP;
END
$$;

-- Runtime indexes for lifecycle-aware evaluation.
CREATE INDEX IF NOT EXISTS idx_device_system_snapshot_cycle
  ON device_system_snapshot (os_type, os_name, os_cycle, capture_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_system_snapshot_lifecycle
  ON device_system_snapshot (os_release_lifecycle_master_id, capture_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_trust_profile_lifecycle
  ON device_trust_profile (os_lifecycle_state, current_score);

CREATE INDEX IF NOT EXISTS idx_posture_eval_run_os_lifecycle
  ON posture_evaluation_run (os_lifecycle_state, evaluated_at DESC);

CREATE INDEX IF NOT EXISTS idx_posture_eval_match_os_lifecycle
  ON posture_evaluation_match (os_lifecycle_state, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_trust_event_os_lifecycle
  ON device_trust_score_event (os_lifecycle_state, event_time DESC);

-- Seed lifecycle master from known OS cycle matrix.
INSERT INTO os_release_lifecycle_master (
  platform_code, os_type, os_name, cycle,
  released_on, eol_on, eeol_on, latest_version,
  support_state, source_name, source_url, notes,
  status, created_by, modified_by
)
VALUES
  ('WINDOWS', 'WINDOWS', NULL, '11-26h1-e', DATE '2026-02-10', DATE '2029-03-13', NULL, '10.0.28000', 'TRACKED', 'endoflife.date', 'https://endoflife.date/windows', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('WINDOWS', 'WINDOWS', NULL, '11-26h1-w', DATE '2026-02-10', DATE '2028-03-14', NULL, '10.0.28000', 'TRACKED', 'endoflife.date', 'https://endoflife.date/windows', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('WINDOWS', 'WINDOWS', NULL, '11-25h2-e', DATE '2025-09-30', DATE '2028-10-10', NULL, '10.0.26200', 'TRACKED', 'endoflife.date', 'https://endoflife.date/windows', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('WINDOWS', 'WINDOWS', NULL, '11-25h2-w', DATE '2025-09-30', DATE '2027-10-12', NULL, '10.0.26200', 'TRACKED', 'endoflife.date', 'https://endoflife.date/windows', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('WINDOWS', 'WINDOWS', NULL, '11-24h2-iot-lts', DATE '2024-10-01', DATE '2034-10-10', NULL, '10.0.26100', 'TRACKED', 'endoflife.date', 'https://endoflife.date/windows', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('MACOS', 'MACOS', NULL, '26', DATE '2025-09-15', NULL, NULL, '26.3', 'SUPPORTED', 'endoflife.date', 'https://endoflife.date/macos', 'Supported date published without fixed EOL', 'ACTIVE', 'system-seed', 'system-seed'),
  ('MACOS', 'MACOS', NULL, '15', DATE '2024-09-16', NULL, NULL, '15.7.4', 'SUPPORTED', 'endoflife.date', 'https://endoflife.date/macos', 'Supported date published without fixed EOL', 'ACTIVE', 'system-seed', 'system-seed'),
  ('MACOS', 'MACOS', NULL, '14', DATE '2023-09-26', NULL, NULL, '14.8.4', 'SUPPORTED', 'endoflife.date', 'https://endoflife.date/macos', 'Supported date published without fixed EOL', 'ACTIVE', 'system-seed', 'system-seed'),
  ('MACOS', 'MACOS', NULL, '13', DATE '2022-10-24', DATE '2025-09-15', NULL, '13.7.8', 'TRACKED', 'endoflife.date', 'https://endoflife.date/macos', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('MACOS', 'MACOS', NULL, '12', DATE '2021-10-25', DATE '2024-09-16', NULL, '12.7.6', 'TRACKED', 'endoflife.date', 'https://endoflife.date/macos', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('CHROMEOS', NULL, NULL, 'NOT_FOUND', NULL, NULL, NULL, NULL, 'NOT_FOUND', 'endoflife.date', NULL, 'Not found in endoflife.date API', 'ACTIVE', 'system-seed', 'system-seed'),

  ('UBUNTU', 'LINUX', 'UBUNTU', '25.10', DATE '2025-10-09', DATE '2026-07-01', NULL, '25.10', 'TRACKED', 'endoflife.date', 'https://endoflife.date/ubuntu', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('UBUNTU', 'LINUX', 'UBUNTU', '25.04', DATE '2025-04-17', DATE '2026-01-17', NULL, '25.04', 'TRACKED', 'endoflife.date', 'https://endoflife.date/ubuntu', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('UBUNTU', 'LINUX', 'UBUNTU', '24.10', DATE '2024-10-10', DATE '2025-07-10', NULL, '24.10', 'TRACKED', 'endoflife.date', 'https://endoflife.date/ubuntu', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('UBUNTU', 'LINUX', 'UBUNTU', '24.04', DATE '2024-04-25', DATE '2029-05-31', NULL, '24.04.4', 'TRACKED', 'endoflife.date', 'https://endoflife.date/ubuntu', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('UBUNTU', 'LINUX', 'UBUNTU', '23.10', DATE '2023-10-12', DATE '2024-07-12', NULL, '23.10', 'TRACKED', 'endoflife.date', 'https://endoflife.date/ubuntu', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('DEBIAN', 'LINUX', 'DEBIAN', '13', DATE '2025-08-09', DATE '2028-08-09', NULL, '13.3', 'TRACKED', 'endoflife.date', 'https://endoflife.date/debian', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('DEBIAN', 'LINUX', 'DEBIAN', '12', DATE '2023-06-10', DATE '2026-06-10', NULL, '12.13', 'TRACKED', 'endoflife.date', 'https://endoflife.date/debian', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('DEBIAN', 'LINUX', 'DEBIAN', '11', DATE '2021-08-14', DATE '2024-08-14', NULL, '11.11', 'TRACKED', 'endoflife.date', 'https://endoflife.date/debian', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('DEBIAN', 'LINUX', 'DEBIAN', '10', DATE '2019-07-06', DATE '2022-09-10', NULL, '10.13', 'TRACKED', 'endoflife.date', 'https://endoflife.date/debian', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('DEBIAN', 'LINUX', 'DEBIAN', '9', DATE '2017-06-17', DATE '2020-07-18', NULL, '9.13', 'TRACKED', 'endoflife.date', 'https://endoflife.date/debian', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('FEDORA', 'LINUX', 'FEDORA', '43', DATE '2025-10-28', DATE '2026-12-09', NULL, '43', 'TRACKED', 'endoflife.date', 'https://endoflife.date/fedora', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('FEDORA', 'LINUX', 'FEDORA', '42', DATE '2025-04-15', DATE '2026-05-13', NULL, '42', 'TRACKED', 'endoflife.date', 'https://endoflife.date/fedora', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('FEDORA', 'LINUX', 'FEDORA', '41', DATE '2024-10-29', DATE '2025-12-15', NULL, '41', 'TRACKED', 'endoflife.date', 'https://endoflife.date/fedora', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('FEDORA', 'LINUX', 'FEDORA', '40', DATE '2024-04-23', DATE '2025-05-13', NULL, '40', 'TRACKED', 'endoflife.date', 'https://endoflife.date/fedora', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('FEDORA', 'LINUX', 'FEDORA', '39', DATE '2023-11-07', DATE '2024-11-26', NULL, '39', 'TRACKED', 'endoflife.date', 'https://endoflife.date/fedora', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('ARCH', 'LINUX', 'ARCH', 'NOT_FOUND', NULL, NULL, NULL, NULL, 'NOT_FOUND', 'endoflife.date', NULL, 'Not found in endoflife.date API', 'ACTIVE', 'system-seed', 'system-seed'),

  ('OPENSUSE', 'LINUX', 'OPENSUSE', '16.0', DATE '2025-10-01', DATE '2027-10-31', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/opensuse', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('OPENSUSE', 'LINUX', 'OPENSUSE', '15.6', DATE '2024-06-12', DATE '2026-04-30', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/opensuse', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('OPENSUSE', 'LINUX', 'OPENSUSE', '15.5', DATE '2023-06-07', DATE '2024-12-31', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/opensuse', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('OPENSUSE', 'LINUX', 'OPENSUSE', '15.4', DATE '2022-06-09', DATE '2023-12-07', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/opensuse', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('OPENSUSE', 'LINUX', 'OPENSUSE', '15.3', DATE '2021-06-02', DATE '2022-12-31', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/opensuse', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('LINUXMINT', 'LINUX', 'LINUXMINT', '22.3', DATE '2026-01-11', DATE '2029-04-30', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/linuxmint', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('LINUXMINT', 'LINUX', 'LINUXMINT', 'LMDE7', DATE '2025-10-14', NULL, NULL, NULL, 'SUPPORTED', 'endoflife.date', 'https://endoflife.date/linuxmint', 'Supported date published without fixed EOL', 'ACTIVE', 'system-seed', 'system-seed'),
  ('LINUXMINT', 'LINUX', 'LINUXMINT', '22.2', DATE '2025-09-04', DATE '2029-04-30', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/linuxmint', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('LINUXMINT', 'LINUX', 'LINUXMINT', '22.1', DATE '2025-01-16', DATE '2029-04-30', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/linuxmint', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('LINUXMINT', 'LINUX', 'LINUXMINT', '22', DATE '2024-07-25', DATE '2029-04-30', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/linuxmint', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('KALI', 'LINUX', 'KALI', 'NOT_FOUND', NULL, NULL, NULL, NULL, 'NOT_FOUND', 'endoflife.date', NULL, 'Not found in endoflife.date API', 'ACTIVE', 'system-seed', 'system-seed'),

  ('ANDROID', 'ANDROID', NULL, '16', DATE '2025-06-10', NULL, NULL, NULL, 'SUPPORTED', 'endoflife.date', 'https://endoflife.date/android', 'Supported date published without fixed EOL', 'ACTIVE', 'system-seed', 'system-seed'),
  ('ANDROID', 'ANDROID', NULL, '15', DATE '2024-09-03', NULL, NULL, NULL, 'SUPPORTED', 'endoflife.date', 'https://endoflife.date/android', 'Supported date published without fixed EOL', 'ACTIVE', 'system-seed', 'system-seed'),
  ('ANDROID', 'ANDROID', NULL, '14', DATE '2023-10-04', NULL, NULL, NULL, 'SUPPORTED', 'endoflife.date', 'https://endoflife.date/android', 'Supported date published without fixed EOL', 'ACTIVE', 'system-seed', 'system-seed'),
  ('ANDROID', 'ANDROID', NULL, '13', DATE '2022-08-15', NULL, NULL, NULL, 'SUPPORTED', 'endoflife.date', 'https://endoflife.date/android', 'Supported date published without fixed EOL', 'ACTIVE', 'system-seed', 'system-seed'),
  ('ANDROID', 'ANDROID', NULL, '12.1', DATE '2022-03-07', DATE '2025-03-03', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/android', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('IOS', 'IOS', NULL, '26', DATE '2025-09-15', NULL, NULL, '26.3', 'SUPPORTED', 'endoflife.date', 'https://endoflife.date/ios', 'Supported date published without fixed EOL', 'ACTIVE', 'system-seed', 'system-seed'),
  ('IOS', 'IOS', NULL, '18', DATE '2024-09-16', NULL, NULL, '18.7.5', 'SUPPORTED', 'endoflife.date', 'https://endoflife.date/ios', 'Supported date published without fixed EOL', 'ACTIVE', 'system-seed', 'system-seed'),
  ('IOS', 'IOS', NULL, '17', DATE '2023-09-18', DATE '2024-11-19', NULL, '17.7.2', 'TRACKED', 'endoflife.date', 'https://endoflife.date/ios', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('IOS', 'IOS', NULL, '16', DATE '2022-09-12', DATE '2025-03-31', NULL, '16.7.14', 'TRACKED', 'endoflife.date', 'https://endoflife.date/ios', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('IOS', 'IOS', NULL, '15', DATE '2021-09-20', DATE '2025-03-31', NULL, '15.8.6', 'TRACKED', 'endoflife.date', 'https://endoflife.date/ios', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('RHEL', 'LINUX', 'RHEL', '10', DATE '2025-05-20', DATE '2035-05-31', NULL, '10.1', 'TRACKED', 'endoflife.date', 'https://endoflife.date/rhel', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('RHEL', 'LINUX', 'RHEL', '9', DATE '2022-05-18', DATE '2032-05-31', NULL, '9.7', 'TRACKED', 'endoflife.date', 'https://endoflife.date/rhel', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('RHEL', 'LINUX', 'RHEL', '8', DATE '2019-05-07', DATE '2029-05-31', NULL, '8.10', 'TRACKED', 'endoflife.date', 'https://endoflife.date/rhel', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('RHEL', 'LINUX', 'RHEL', '7', DATE '2014-06-10', DATE '2024-06-30', NULL, '7.9', 'TRACKED', 'endoflife.date', 'https://endoflife.date/rhel', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('RHEL', 'LINUX', 'RHEL', '6', DATE '2010-11-10', DATE '2020-11-30', NULL, '6.10', 'TRACKED', 'endoflife.date', 'https://endoflife.date/rhel', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('CENTOS', 'LINUX', 'CENTOS', '8', DATE '2019-09-24', DATE '2021-12-31', NULL, '8 (2111)', 'TRACKED', 'endoflife.date', 'https://endoflife.date/centos', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('CENTOS', 'LINUX', 'CENTOS', '7', DATE '2014-07-07', DATE '2024-06-30', NULL, '7 (2009)', 'TRACKED', 'endoflife.date', 'https://endoflife.date/centos', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('CENTOS', 'LINUX', 'CENTOS', '6', DATE '2011-07-10', DATE '2020-11-30', NULL, '6.10', 'TRACKED', 'endoflife.date', 'https://endoflife.date/centos', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('CENTOS', 'LINUX', 'CENTOS', '5', DATE '2007-04-12', DATE '2017-03-31', NULL, '5.11', 'TRACKED', 'endoflife.date', 'https://endoflife.date/centos', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('ROCKY', 'LINUX', 'ROCKY', '10', DATE '2025-06-11', DATE '2035-05-31', NULL, '10.1', 'TRACKED', 'endoflife.date', 'https://endoflife.date/rocky-linux', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('ROCKY', 'LINUX', 'ROCKY', '9', DATE '2022-07-14', DATE '2032-05-31', NULL, '9.7', 'TRACKED', 'endoflife.date', 'https://endoflife.date/rocky-linux', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('ROCKY', 'LINUX', 'ROCKY', '8', DATE '2021-05-01', DATE '2029-05-31', NULL, '8.10', 'TRACKED', 'endoflife.date', 'https://endoflife.date/rocky-linux', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('ALMALINUX', 'LINUX', 'ALMALINUX', '10', DATE '2025-05-27', DATE '2035-05-31', NULL, '10.1', 'TRACKED', 'endoflife.date', 'https://endoflife.date/almalinux', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('ALMALINUX', 'LINUX', 'ALMALINUX', '9', DATE '2022-05-26', DATE '2032-05-31', NULL, '9.7', 'TRACKED', 'endoflife.date', 'https://endoflife.date/almalinux', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('ALMALINUX', 'LINUX', 'ALMALINUX', '8', DATE '2021-03-30', DATE '2029-03-01', NULL, '8.10', 'TRACKED', 'endoflife.date', 'https://endoflife.date/almalinux', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('FREEBSD', NULL, NULL, '15', DATE '2025-12-02', DATE '2029-12-31', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/freebsd', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('FREEBSD', NULL, NULL, '15.0', DATE '2025-12-02', DATE '2026-09-30', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/freebsd', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('FREEBSD', NULL, NULL, '14.3', DATE '2025-06-10', DATE '2026-06-30', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/freebsd', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('FREEBSD', NULL, NULL, '13.5', DATE '2025-03-11', DATE '2026-04-30', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/freebsd', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('FREEBSD', NULL, NULL, '14.2', DATE '2024-12-03', DATE '2025-09-30', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/freebsd', NULL, 'ACTIVE', 'system-seed', 'system-seed'),

  ('OPENBSD', NULL, NULL, '7.8', DATE '2025-10-22', DATE '2026-11-01', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/openbsd', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('OPENBSD', NULL, NULL, '7.7', DATE '2025-04-28', DATE '2026-05-01', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/openbsd', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('OPENBSD', NULL, NULL, '7.6', DATE '2024-10-08', DATE '2025-10-22', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/openbsd', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('OPENBSD', NULL, NULL, '7.5', DATE '2024-04-05', DATE '2025-04-28', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/openbsd', NULL, 'ACTIVE', 'system-seed', 'system-seed'),
  ('OPENBSD', NULL, NULL, '7.4', DATE '2023-10-16', DATE '2024-10-08', NULL, NULL, 'TRACKED', 'endoflife.date', 'https://endoflife.date/openbsd', NULL, 'ACTIVE', 'system-seed', 'system-seed')
ON CONFLICT (platform_code, cycle)
DO UPDATE
SET
  os_type = EXCLUDED.os_type,
  os_name = EXCLUDED.os_name,
  released_on = EXCLUDED.released_on,
  eol_on = EXCLUDED.eol_on,
  eeol_on = EXCLUDED.eeol_on,
  latest_version = EXCLUDED.latest_version,
  support_state = EXCLUDED.support_state,
  source_name = EXCLUDED.source_name,
  source_url = EXCLUDED.source_url,
  notes = EXCLUDED.notes,
  status = EXCLUDED.status,
  modified_at = now(),
  modified_by = EXCLUDED.modified_by;
