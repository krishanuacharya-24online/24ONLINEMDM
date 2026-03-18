CREATE TABLE IF NOT EXISTS trust_score_decision_policy (
  id BIGSERIAL PRIMARY KEY,

  policy_name TEXT NOT NULL,
  score_min SMALLINT NOT NULL CHECK (score_min BETWEEN 0 AND 100),
  score_max SMALLINT NOT NULL CHECK (score_max BETWEEN 0 AND 100),
  decision_action TEXT NOT NULL CHECK (decision_action IN ('ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY')),
  remediation_required BOOLEAN NOT NULL DEFAULT false,
  response_message TEXT,

  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  effective_to TIMESTAMPTZ,
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL,

  CONSTRAINT ck_trust_decision_score_range
    CHECK (score_min <= score_max),
  CONSTRAINT ck_trust_decision_window
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_trust_score_decision_policy
  ON trust_score_decision_policy (policy_name, score_min, score_max, decision_action)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_trust_score_decision_active
  ON trust_score_decision_policy (status, effective_from, effective_to, score_min, score_max)
  WHERE is_deleted = false;
