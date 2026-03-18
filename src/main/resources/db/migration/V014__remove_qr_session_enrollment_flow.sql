ALTER TABLE device_enrollment
    DROP COLUMN IF EXISTS qr_session_id;

DROP TABLE IF EXISTS device_qr_enroll_session;
