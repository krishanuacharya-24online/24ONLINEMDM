DO $$
DECLARE
    constraint_row RECORD;
BEGIN
    FOR constraint_row IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = current_schema()
          AND t.relname = 'device_setup_key'
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%status%'
          AND pg_get_constraintdef(c.oid) ILIKE '%ACTIVE%'
          AND pg_get_constraintdef(c.oid) ILIKE '%REVOKED%'
          AND pg_get_constraintdef(c.oid) ILIKE '%EXPIRED%'
    LOOP
        EXECUTE format('ALTER TABLE device_setup_key DROP CONSTRAINT %I', constraint_row.conname);
    END LOOP;
END $$;

ALTER TABLE device_setup_key
    ADD CONSTRAINT ck_device_setup_key_status
        CHECK (status IN ('ACTIVE', 'CLAIMED', 'REVOKED', 'EXPIRED'));
