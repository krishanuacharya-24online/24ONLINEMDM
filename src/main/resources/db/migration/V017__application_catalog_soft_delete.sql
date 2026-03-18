ALTER TABLE application_catalog
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_application_catalog_active_lookup
    ON application_catalog (os_type, package_id_norm, app_name_norm)
    WHERE is_deleted = false;
