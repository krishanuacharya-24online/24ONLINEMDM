-- Relax enum-style OS/device target checks so posture ingest and policy matching
-- can persist any nonblank target values defined by policy.

DO $$
DECLARE
  rec RECORD;
BEGIN
  FOR rec IN
    SELECT cl.relname AS table_name, c.conname AS constraint_name
    FROM pg_constraint c
    JOIN pg_class cl ON cl.oid = c.conrelid
    WHERE c.contype = 'c'
      AND cl.relname IN (
        'system_information_rule',
        'device_trust_profile',
        'device_system_snapshot',
        'reject_application_list',
        'device_installed_application',
        'remediation_rule'
      )
      AND pg_get_constraintdef(c.oid) ILIKE '%ANDROID%'
      AND (
        pg_get_constraintdef(c.oid) ILIKE '%os_type%'
        OR pg_get_constraintdef(c.oid) ILIKE '%app_os_type%'
      )
  LOOP
    EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', rec.table_name, rec.constraint_name);
  END LOOP;
END
$$;

DO $$
DECLARE
  rec RECORD;
BEGIN
  FOR rec IN
    SELECT cl.relname AS table_name, c.conname AS constraint_name
    FROM pg_constraint c
    JOIN pg_class cl ON cl.oid = c.conrelid
    WHERE c.contype = 'c'
      AND cl.relname IN (
        'system_information_rule',
        'device_trust_profile',
        'device_system_snapshot',
        'remediation_rule'
      )
      AND pg_get_constraintdef(c.oid) ILIKE '%device_type%'
      AND pg_get_constraintdef(c.oid) ILIKE '%PHONE%'
  LOOP
    EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', rec.table_name, rec.constraint_name);
  END LOOP;
END
$$;

ALTER TABLE system_information_rule
  DROP CONSTRAINT IF EXISTS ck_system_rule_os_type_nonblank,
  DROP CONSTRAINT IF EXISTS ck_system_rule_device_type_nonblank;

ALTER TABLE device_trust_profile
  DROP CONSTRAINT IF EXISTS ck_device_profile_os_type_nonblank,
  DROP CONSTRAINT IF EXISTS ck_device_profile_device_type_nonblank;

ALTER TABLE device_system_snapshot
  DROP CONSTRAINT IF EXISTS ck_snapshot_os_type_nonblank,
  DROP CONSTRAINT IF EXISTS ck_snapshot_device_type_nonblank;

ALTER TABLE reject_application_list
  DROP CONSTRAINT IF EXISTS ck_reject_app_os_type_nonblank;

ALTER TABLE device_installed_application
  DROP CONSTRAINT IF EXISTS ck_installed_app_os_type_nonblank;

ALTER TABLE remediation_rule
  DROP CONSTRAINT IF EXISTS ck_remediation_rule_os_type_nonblank,
  DROP CONSTRAINT IF EXISTS ck_remediation_rule_device_type_nonblank;

ALTER TABLE system_information_rule
  ADD CONSTRAINT ck_system_rule_os_type_nonblank
    CHECK (length(trim(os_type)) > 0),
  ADD CONSTRAINT ck_system_rule_device_type_nonblank
    CHECK (device_type IS NULL OR length(trim(device_type)) > 0);

ALTER TABLE device_trust_profile
  ADD CONSTRAINT ck_device_profile_os_type_nonblank
    CHECK (length(trim(os_type)) > 0),
  ADD CONSTRAINT ck_device_profile_device_type_nonblank
    CHECK (device_type IS NULL OR length(trim(device_type)) > 0);

ALTER TABLE device_system_snapshot
  ADD CONSTRAINT ck_snapshot_os_type_nonblank
    CHECK (length(trim(os_type)) > 0),
  ADD CONSTRAINT ck_snapshot_device_type_nonblank
    CHECK (device_type IS NULL OR length(trim(device_type)) > 0);

ALTER TABLE reject_application_list
  ADD CONSTRAINT ck_reject_app_os_type_nonblank
    CHECK (length(trim(app_os_type)) > 0);

ALTER TABLE device_installed_application
  ADD CONSTRAINT ck_installed_app_os_type_nonblank
    CHECK (length(trim(app_os_type)) > 0);

ALTER TABLE remediation_rule
  ADD CONSTRAINT ck_remediation_rule_os_type_nonblank
    CHECK (os_type IS NULL OR length(trim(os_type)) > 0),
  ADD CONSTRAINT ck_remediation_rule_device_type_nonblank
    CHECK (device_type IS NULL OR length(trim(device_type)) > 0);
