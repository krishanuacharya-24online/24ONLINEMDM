ALTER TABLE device_posture_payload
    ADD COLUMN IF NOT EXISTS idempotency_key TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_posture_payload_idempotency
    ON device_posture_payload (COALESCE(tenant_id, ''), device_external_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_posture_payload_idempotency_lookup
    ON device_posture_payload (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE device_posture_payload
    DROP CONSTRAINT IF EXISTS device_posture_payload_process_status_check;

ALTER TABLE device_posture_payload
    DROP CONSTRAINT IF EXISTS ck_device_posture_payload_process_status;

ALTER TABLE device_posture_payload
    ADD CONSTRAINT ck_device_posture_payload_process_status
    CHECK (process_status IN ('RECEIVED', 'QUEUED', 'VALIDATED', 'EVALUATED', 'FAILED'));

INSERT INTO lkp_master (lookup_type, code, description)
VALUES ('lkp_payload_process_status', 'QUEUED', 'Payload accepted and queued for asynchronous evaluation')
ON CONFLICT (lookup_type, code) DO UPDATE
SET description = EXCLUDED.description;
