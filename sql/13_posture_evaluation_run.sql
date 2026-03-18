CREATE TABLE IF NOT EXISTS posture_evaluation_run (
  id BIGSERIAL PRIMARY KEY,

  device_posture_payload_id BIGINT NOT NULL UNIQUE REFERENCES device_posture_payload(id) ON DELETE CASCADE,
  device_trust_profile_id BIGINT NOT NULL REFERENCES device_trust_profile(id) ON DELETE CASCADE,
  trust_score_decision_policy_id BIGINT REFERENCES trust_score_decision_policy(id),
  os_release_lifecycle_master_id BIGINT,
  os_lifecycle_state TEXT,

  evaluation_status TEXT NOT NULL DEFAULT 'IN_PROGRESS'
    CHECK (evaluation_status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
  trust_score_before SMALLINT NOT NULL CHECK (trust_score_before BETWEEN 0 AND 100),
  trust_score_delta_total SMALLINT NOT NULL DEFAULT 0 CHECK (trust_score_delta_total BETWEEN -1000 AND 1000),
  trust_score_after SMALLINT NOT NULL CHECK (trust_score_after BETWEEN 0 AND 100),
  decision_action TEXT NOT NULL CHECK (decision_action IN ('ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY')),
  decision_reason TEXT,
  remediation_required BOOLEAN NOT NULL DEFAULT false,

  matched_rule_count INTEGER NOT NULL DEFAULT 0 CHECK (matched_rule_count >= 0),
  matched_app_count INTEGER NOT NULL DEFAULT 0 CHECK (matched_app_count >= 0),
  evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  responded_at TIMESTAMPTZ,
  response_payload JSONB,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'rule-engine',

  CONSTRAINT ck_eval_score_math
    CHECK (trust_score_after = LEAST(100, GREATEST(0, trust_score_before + trust_score_delta_total))),
  CONSTRAINT ck_eval_response_time
    CHECK (responded_at IS NULL OR responded_at >= evaluated_at),
  CONSTRAINT ck_eval_run_os_lifecycle_state
    CHECK (
      os_lifecycle_state IS NULL
      OR os_lifecycle_state IN ('SUPPORTED', 'EOL', 'EEOL', 'NOT_TRACKED')
    )
);

CREATE INDEX IF NOT EXISTS idx_posture_eval_run_profile_time
  ON posture_evaluation_run (device_trust_profile_id, evaluated_at DESC);

CREATE INDEX IF NOT EXISTS idx_posture_eval_run_status
  ON posture_evaluation_run (evaluation_status, evaluated_at DESC);

CREATE INDEX IF NOT EXISTS idx_posture_eval_run_decision
  ON posture_evaluation_run (decision_action, evaluated_at DESC);

CREATE INDEX IF NOT EXISTS idx_posture_eval_run_os_lifecycle
  ON posture_evaluation_run (os_lifecycle_state, evaluated_at DESC);

CREATE INDEX IF NOT EXISTS idx_posture_eval_run_response
  ON posture_evaluation_run USING gin (response_payload);
