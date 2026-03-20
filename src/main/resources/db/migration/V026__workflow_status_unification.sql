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
          AND t.relname = 'posture_evaluation_remediation'
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%remediation_status%'
    LOOP
        EXECUTE format('ALTER TABLE posture_evaluation_remediation DROP CONSTRAINT %I', constraint_row.conname);
    END LOOP;
END $$;

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
          AND t.relname = 'device_decision_response'
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%delivery_status%'
    LOOP
        EXECUTE format('ALTER TABLE device_decision_response DROP CONSTRAINT %I', constraint_row.conname);
    END LOOP;
END $$;

UPDATE posture_evaluation_remediation
SET remediation_status = CASE remediation_status
        WHEN 'PENDING' THEN 'PROPOSED'
        WHEN 'SENT' THEN 'DELIVERED'
        WHEN 'ACKED' THEN 'USER_ACKNOWLEDGED'
        WHEN 'SKIPPED' THEN 'CLOSED'
        WHEN 'FAILED' THEN 'STILL_OPEN'
        ELSE remediation_status
    END,
    completed_at = CASE remediation_status
        WHEN 'ACKED' THEN COALESCE(completed_at, created_at)
        WHEN 'SKIPPED' THEN COALESCE(completed_at, created_at)
        WHEN 'PENDING' THEN NULL
        WHEN 'SENT' THEN NULL
        WHEN 'FAILED' THEN NULL
        ELSE completed_at
    END;

UPDATE device_decision_response
SET delivery_status = CASE delivery_status
        WHEN 'SENT' THEN 'DELIVERED'
        WHEN 'ACKED' THEN 'ACKNOWLEDGED'
        ELSE delivery_status
    END,
    sent_at = CASE
        WHEN delivery_status IN ('SENT', 'ACKED', 'FAILED', 'TIMEOUT') AND sent_at IS NULL
            THEN COALESCE(acknowledged_at, created_at)
        ELSE sent_at
    END,
    acknowledged_at = CASE
        WHEN delivery_status = 'ACKED' AND acknowledged_at IS NULL
            THEN COALESCE(sent_at, created_at)
        ELSE acknowledged_at
    END;

ALTER TABLE posture_evaluation_remediation
    ALTER COLUMN remediation_status SET DEFAULT 'PROPOSED';

ALTER TABLE posture_evaluation_remediation
    ADD CONSTRAINT ck_posture_evaluation_remediation_status
        CHECK (remediation_status IN (
            'PROPOSED',
            'DELIVERED',
            'USER_ACKNOWLEDGED',
            'STILL_OPEN',
            'RESOLVED_ON_RESCAN',
            'CLOSED'
        ));

ALTER TABLE device_decision_response
    ALTER COLUMN delivery_status SET DEFAULT 'PENDING';

ALTER TABLE device_decision_response
    ADD CONSTRAINT ck_device_decision_response_delivery_status
        CHECK (delivery_status IN (
            'PENDING',
            'DELIVERED',
            'ACKNOWLEDGED',
            'FAILED',
            'TIMEOUT'
        ));

DELETE FROM lkp_master
WHERE lookup_type IN ('lkp_remediation_status', 'lkp_delivery_status');

INSERT INTO lkp_master (lookup_type, code, description)
VALUES
    ('lkp_remediation_status', 'PROPOSED', 'Remediation has been proposed by evaluation'),
    ('lkp_remediation_status', 'DELIVERED', 'Remediation guidance has been delivered to the agent'),
    ('lkp_remediation_status', 'USER_ACKNOWLEDGED', 'User acknowledged the remediation guidance'),
    ('lkp_remediation_status', 'STILL_OPEN', 'Remediation remains unresolved on a later scan'),
    ('lkp_remediation_status', 'RESOLVED_ON_RESCAN', 'Remediation was resolved on a later scan'),
    ('lkp_remediation_status', 'CLOSED', 'Remediation was closed without further action'),
    ('lkp_delivery_status', 'PENDING', 'Decision response is ready but not yet delivered'),
    ('lkp_delivery_status', 'DELIVERED', 'Decision response was delivered to the agent'),
    ('lkp_delivery_status', 'ACKNOWLEDGED', 'Decision response was acknowledged by the agent'),
    ('lkp_delivery_status', 'FAILED', 'Decision delivery failed'),
    ('lkp_delivery_status', 'TIMEOUT', 'Decision delivery timed out');
