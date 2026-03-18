CREATE TABLE IF NOT EXISTS device_decision_response (
  id BIGSERIAL PRIMARY KEY,

  posture_evaluation_run_id BIGINT NOT NULL UNIQUE REFERENCES posture_evaluation_run(id) ON DELETE CASCADE,
  tenant_id TEXT,
  device_external_id TEXT NOT NULL,
  decision_action TEXT NOT NULL CHECK (decision_action IN ('ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY')),
  trust_score SMALLINT NOT NULL CHECK (trust_score BETWEEN 0 AND 100),
  remediation_required BOOLEAN NOT NULL DEFAULT false,

  response_payload JSONB NOT NULL,
  delivery_status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK (delivery_status IN ('PENDING', 'SENT', 'ACKED', 'FAILED', 'TIMEOUT')),
  sent_at TIMESTAMPTZ,
  acknowledged_at TIMESTAMPTZ,
  error_message TEXT,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'policy-service',

  CONSTRAINT ck_device_decision_response_time
    CHECK (
      (acknowledged_at IS NULL)
      OR (sent_at IS NOT NULL AND acknowledged_at >= sent_at)
    )
);

CREATE INDEX IF NOT EXISTS idx_device_decision_response_device_time
  ON device_decision_response (COALESCE(tenant_id, ''), device_external_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_device_decision_response_delivery
  ON device_decision_response (delivery_status, created_at DESC);
