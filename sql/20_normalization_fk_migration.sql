-- Backfill and wire normalized FKs on top of existing tables.
-- Simplified: one data-driven FK loop instead of repetitive statements.

INSERT INTO lkp_master (lookup_type, code, description)
SELECT DISTINCT 'lkp_app_category', app_category, app_category
FROM reject_application_list
WHERE app_category IS NOT NULL
ON CONFLICT (lookup_type, code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO lkp_master (lookup_type, code, description)
SELECT DISTINCT 'lkp_threat_type', threat_type, threat_type
FROM reject_application_list
WHERE threat_type IS NOT NULL
ON CONFLICT (lookup_type, code) DO UPDATE
SET description = EXCLUDED.description;

ALTER TABLE reject_application_list
  ADD COLUMN IF NOT EXISTS application_catalog_id BIGINT;

ALTER TABLE device_installed_application
  ADD COLUMN IF NOT EXISTS application_catalog_id BIGINT;

ALTER TABLE system_information_rule
  ADD COLUMN IF NOT EXISTS os_name TEXT;

ALTER TABLE device_system_snapshot
  ADD COLUMN IF NOT EXISTS os_name TEXT;

ALTER TABLE device_trust_profile
  ADD COLUMN IF NOT EXISTS os_name TEXT;

DO $$
DECLARE
  rec RECORD;
BEGIN
  FOR rec IN
    SELECT *
    FROM (
      VALUES
        ('system_information_rule', 'ck_sysrule_os_name_linux', 'CHECK (os_name IS NULL OR os_type = ''LINUX'')'),
        ('device_system_snapshot', 'ck_snapshot_os_name_linux', 'CHECK (os_name IS NULL OR os_type = ''LINUX'')'),
        ('device_trust_profile', 'ck_device_profile_os_name_linux', 'CHECK (os_name IS NULL OR os_type = ''LINUX'')')
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

INSERT INTO application_catalog (os_type, package_id, app_name, publisher)
SELECT DISTINCT
  r.app_os_type,
  NULLIF(r.package_id, ''),
  r.app_name,
  NULLIF(r.publisher, '')
FROM reject_application_list r
ON CONFLICT (os_type, package_id_norm, app_name_norm) DO UPDATE
SET
  publisher = COALESCE(application_catalog.publisher, EXCLUDED.publisher),
  modified_at = now();

INSERT INTO application_catalog (os_type, package_id, app_name, publisher)
SELECT DISTINCT
  a.app_os_type,
  NULLIF(a.package_id, ''),
  a.app_name,
  NULLIF(a.publisher, '')
FROM device_installed_application a
ON CONFLICT (os_type, package_id_norm, app_name_norm) DO UPDATE
SET
  publisher = COALESCE(application_catalog.publisher, EXCLUDED.publisher),
  modified_at = now();

UPDATE reject_application_list r
SET application_catalog_id = c.id
FROM application_catalog c
WHERE r.application_catalog_id IS NULL
  AND c.os_type = r.app_os_type
  AND c.package_id_norm = COALESCE(r.package_id, '')
  AND c.app_name_norm = lower(r.app_name);

UPDATE device_installed_application a
SET application_catalog_id = c.id
FROM application_catalog c
WHERE a.application_catalog_id IS NULL
  AND c.os_type = a.app_os_type
  AND c.package_id_norm = COALESCE(a.package_id, '')
  AND c.app_name_norm = lower(a.app_name);

CREATE INDEX IF NOT EXISTS idx_reject_application_catalog_id
  ON reject_application_list (application_catalog_id);

CREATE INDEX IF NOT EXISTS idx_installed_application_catalog_id
  ON device_installed_application (application_catalog_id);

DO $$
DECLARE
  fk RECORD;
BEGIN
  FOR fk IN
    SELECT *
    FROM (
      VALUES
        ('reject_application_list', 'fk_reject_application_catalog', 'FOREIGN KEY (application_catalog_id) REFERENCES application_catalog(id)'),
        ('device_installed_application', 'fk_installed_application_catalog', 'FOREIGN KEY (application_catalog_id) REFERENCES application_catalog(id)')
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

CREATE OR REPLACE FUNCTION mdm_assign_application_catalog_id()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.application_catalog_id IS NULL THEN
    INSERT INTO application_catalog (os_type, package_id, app_name, publisher)
    VALUES (
      NEW.app_os_type,
      NULLIF(NEW.package_id, ''),
      NEW.app_name,
      NULLIF(NEW.publisher, '')
    )
    ON CONFLICT (os_type, package_id_norm, app_name_norm) DO UPDATE
    SET
      publisher = COALESCE(application_catalog.publisher, EXCLUDED.publisher),
      modified_at = now()
    RETURNING id INTO NEW.application_catalog_id;
  END IF;

  RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS trg_reject_assign_application_catalog ON reject_application_list;
CREATE TRIGGER trg_reject_assign_application_catalog
BEFORE INSERT OR UPDATE OF app_os_type, package_id, app_name, publisher, application_catalog_id
ON reject_application_list
FOR EACH ROW
EXECUTE FUNCTION mdm_assign_application_catalog_id();

DROP TRIGGER IF EXISTS trg_installed_assign_application_catalog ON device_installed_application;
CREATE TRIGGER trg_installed_assign_application_catalog
BEFORE INSERT OR UPDATE OF app_os_type, package_id, app_name, publisher, application_catalog_id
ON device_installed_application
FOR EACH ROW
EXECUTE FUNCTION mdm_assign_application_catalog_id();
