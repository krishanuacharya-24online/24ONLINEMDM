CREATE TABLE IF NOT EXISTS device_posture_payload (
  id BIGSERIAL PRIMARY KEY,

  tenant_id TEXT,
  device_external_id TEXT NOT NULL,
  agent_id TEXT,
  payload_version TEXT,
  payload_hash TEXT,
  idempotency_key TEXT,
  payload_json JSONB NOT NULL,

  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  process_status TEXT NOT NULL DEFAULT 'RECEIVED'
    CHECK (process_status IN ('RECEIVED', 'QUEUED', 'VALIDATED', 'EVALUATED', 'FAILED')),
  process_error TEXT,
  processed_at TIMESTAMPTZ,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'agent-ingest',

  CONSTRAINT ck_payload_processed_time
    CHECK (processed_at IS NULL OR processed_at >= received_at)
);

CREATE INDEX IF NOT EXISTS idx_posture_payload_device_time
  ON device_posture_payload (COALESCE(tenant_id, ''), device_external_id, received_at DESC);

CREATE INDEX IF NOT EXISTS idx_posture_payload_status_time
  ON device_posture_payload (process_status, received_at DESC);

CREATE INDEX IF NOT EXISTS idx_posture_payload_hash
  ON device_posture_payload (payload_hash);

CREATE UNIQUE INDEX IF NOT EXISTS uq_posture_payload_idempotency
  ON device_posture_payload (COALESCE(tenant_id, ''), device_external_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
