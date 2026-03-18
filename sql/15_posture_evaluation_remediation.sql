CREATE TABLE IF NOT EXISTS posture_evaluation_remediation (
  id BIGSERIAL PRIMARY KEY,

  posture_evaluation_run_id BIGINT NOT NULL REFERENCES posture_evaluation_run(id) ON DELETE CASCADE,
  remediation_rule_id BIGINT NOT NULL REFERENCES remediation_rule(id),
  posture_evaluation_match_id BIGINT REFERENCES posture_evaluation_match(id) ON DELETE SET NULL,
  source_type TEXT NOT NULL CHECK (source_type IN ('MATCH', 'DECISION')),

  remediation_status TEXT NOT NULL DEFAULT 'PENDING'
    CHECK (remediation_status IN ('PENDING', 'SENT', 'ACKED', 'SKIPPED', 'FAILED')),
  due_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  instruction_override JSONB,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'rule-engine',

  CONSTRAINT ck_posture_eval_remediation_time
    CHECK (completed_at IS NULL OR due_at IS NULL OR completed_at >= due_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_posture_eval_remediation
  ON posture_evaluation_remediation (posture_evaluation_run_id, remediation_rule_id, COALESCE(posture_evaluation_match_id, 0));

CREATE INDEX IF NOT EXISTS idx_posture_eval_remediation_run_status
  ON posture_evaluation_remediation (posture_evaluation_run_id, remediation_status);

CREATE INDEX IF NOT EXISTS idx_posture_eval_remediation_rule
  ON posture_evaluation_remediation (remediation_rule_id, remediation_status);
