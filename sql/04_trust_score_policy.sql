CREATE TABLE IF NOT EXISTS trust_score_policy (
  id BIGSERIAL PRIMARY KEY,

  policy_code TEXT NOT NULL,
  source_type TEXT NOT NULL CHECK (source_type IN ('SYSTEM_RULE', 'REJECT_APPLICATION', 'POSTURE_SIGNAL', 'MANUAL')),
  signal_key TEXT NOT NULL,

  severity SMALLINT CHECK (severity BETWEEN 1 AND 5),
  compliance_action TEXT CHECK (compliance_action IN ('ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY')),
  score_delta SMALLINT NOT NULL CHECK (score_delta BETWEEN -1000 AND 1000),
  weight NUMERIC(8, 4) NOT NULL DEFAULT 1.0000 CHECK (weight > 0 AND weight <= 10),

  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  effective_to TIMESTAMPTZ,
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL,

  CONSTRAINT ck_trust_policy_effective_window
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);

-- Lifecycle signal convention used by evaluator:
-- - source_type = 'POSTURE_SIGNAL'
-- - signal_key IN ('OS_EOL', 'OS_EEOL', 'OS_NOT_TRACKED')

CREATE UNIQUE INDEX IF NOT EXISTS uq_trust_policy_code
  ON trust_score_policy (policy_code)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_trust_policy_signal_active
  ON trust_score_policy (source_type, signal_key, COALESCE(severity, 0), COALESCE(compliance_action, ''))
  WHERE status = 'ACTIVE' AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_trust_policy_lookup
  ON trust_score_policy (source_type, signal_key, status, effective_from, effective_to)
  WHERE is_deleted = false;
