ALTER TABLE system_information_rule
    DROP CONSTRAINT IF EXISTS ck_sysrule_os_name_linux;

ALTER TABLE device_system_snapshot
    DROP CONSTRAINT IF EXISTS ck_snapshot_os_name_linux;

ALTER TABLE device_trust_profile
    DROP CONSTRAINT IF EXISTS ck_device_profile_os_name_linux;

ALTER TABLE system_information_rule
    DROP CONSTRAINT IF EXISTS ck_sysrule_os_name_requires_type;

ALTER TABLE device_system_snapshot
    DROP CONSTRAINT IF EXISTS ck_snapshot_os_name_requires_type;

ALTER TABLE device_trust_profile
    DROP CONSTRAINT IF EXISTS ck_device_profile_os_name_requires_type;

ALTER TABLE system_information_rule
    ADD CONSTRAINT ck_sysrule_os_name_requires_type
        CHECK (os_name IS NULL OR os_type IS NOT NULL);

ALTER TABLE device_system_snapshot
    ADD CONSTRAINT ck_snapshot_os_name_requires_type
        CHECK (os_name IS NULL OR os_type IS NOT NULL);

ALTER TABLE device_trust_profile
    ADD CONSTRAINT ck_device_profile_os_name_requires_type
        CHECK (os_name IS NULL OR os_type IS NOT NULL);
