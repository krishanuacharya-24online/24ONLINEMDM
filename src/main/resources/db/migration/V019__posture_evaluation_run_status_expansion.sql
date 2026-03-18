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
          AND t.relname = 'posture_evaluation_run'
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%evaluation_status%'
    LOOP
        EXECUTE format('ALTER TABLE posture_evaluation_run DROP CONSTRAINT %I', constraint_row.conname);
    END LOOP;
END $$;

ALTER TABLE posture_evaluation_run
    ADD CONSTRAINT ck_posture_evaluation_run_status
        CHECK (evaluation_status IN (
            'QUEUED',
            'VALIDATING',
            'RUNNING',
            'IN_PROGRESS',
            'COMPLETED',
            'FAILED',
            'CANCELLED'
        ));

INSERT INTO lkp_master (lookup_type, code, description)
VALUES
    ('lkp_evaluation_status', 'QUEUED', 'Evaluation request is queued'),
    ('lkp_evaluation_status', 'VALIDATING', 'Evaluation input validation in progress'),
    ('lkp_evaluation_status', 'RUNNING', 'Evaluation is running'),
    ('lkp_evaluation_status', 'CANCELLED', 'Evaluation was cancelled')
ON CONFLICT (lookup_type, code) DO UPDATE
SET description = EXCLUDED.description;
