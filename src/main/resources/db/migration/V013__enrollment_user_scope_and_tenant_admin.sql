ALTER TABLE device_setup_key
    ADD COLUMN IF NOT EXISTS issued_by_user_id BIGINT REFERENCES auth_user(id),
    ADD COLUMN IF NOT EXISTS target_user_id BIGINT REFERENCES auth_user(id);

CREATE INDEX IF NOT EXISTS idx_device_setup_key_target_user
    ON device_setup_key (target_user_id, status, expires_at DESC);

ALTER TABLE device_qr_enroll_session
    ADD COLUMN IF NOT EXISTS issued_by_user_id BIGINT REFERENCES auth_user(id),
    ADD COLUMN IF NOT EXISTS target_user_id BIGINT REFERENCES auth_user(id);

CREATE INDEX IF NOT EXISTS idx_device_qr_session_target_user
    ON device_qr_enroll_session (target_user_id, status, expires_at DESC);

ALTER TABLE device_enrollment
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT REFERENCES auth_user(id);

CREATE INDEX IF NOT EXISTS idx_device_enrollment_tenant_owner_status
    ON device_enrollment (COALESCE(tenant_id, ''), owner_user_id, status, enrolled_at DESC);
