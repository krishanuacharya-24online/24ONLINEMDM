CREATE TABLE IF NOT EXISTS rule_remediation_mapping (
  id BIGSERIAL PRIMARY KEY,

  source_type TEXT NOT NULL
    CHECK (source_type IN ('SYSTEM_RULE', 'REJECT_APPLICATION', 'TRUST_POLICY', 'DECISION')),
  system_information_rule_id BIGINT REFERENCES system_information_rule(id) ON DELETE CASCADE,
  reject_application_list_id BIGINT REFERENCES reject_application_list(id) ON DELETE CASCADE,
  trust_score_policy_id BIGINT REFERENCES trust_score_policy(id) ON DELETE CASCADE,
  decision_action TEXT CHECK (decision_action IN ('ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY')),

  remediation_rule_id BIGINT NOT NULL REFERENCES remediation_rule(id) ON DELETE CASCADE,
  enforce_mode TEXT NOT NULL DEFAULT 'ADVISORY' CHECK (enforce_mode IN ('AUTO', 'MANUAL', 'ADVISORY')),
  rank_order SMALLINT NOT NULL DEFAULT 1 CHECK (rank_order BETWEEN 1 AND 1000),

  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  effective_to TIMESTAMPTZ,
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL,

  CONSTRAINT ck_rule_remediation_window
    CHECK (effective_to IS NULL OR effective_to > effective_from),

  CONSTRAINT ck_rule_remediation_target
    CHECK (
      (source_type = 'SYSTEM_RULE'
        AND system_information_rule_id IS NOT NULL
        AND reject_application_list_id IS NULL
        AND trust_score_policy_id IS NULL
        AND decision_action IS NULL)
      OR
      (source_type = 'REJECT_APPLICATION'
        AND system_information_rule_id IS NULL
        AND reject_application_list_id IS NOT NULL
        AND trust_score_policy_id IS NULL
        AND decision_action IS NULL)
      OR
      (source_type = 'TRUST_POLICY'
        AND system_information_rule_id IS NULL
        AND reject_application_list_id IS NULL
        AND trust_score_policy_id IS NOT NULL
        AND decision_action IS NULL)
      OR
      (source_type = 'DECISION'
        AND system_information_rule_id IS NULL
        AND reject_application_list_id IS NULL
        AND trust_score_policy_id IS NULL
        AND decision_action IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rule_remediation_mapping
  ON rule_remediation_mapping (
    source_type,
    COALESCE(system_information_rule_id, 0),
    COALESCE(reject_application_list_id, 0),
    COALESCE(trust_score_policy_id, 0),
    COALESCE(decision_action, ''),
    remediation_rule_id,
    rank_order
  )
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_rule_remediation_system_rule
  ON rule_remediation_mapping (system_information_rule_id, status, rank_order)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_rule_remediation_reject_app
  ON rule_remediation_mapping (reject_application_list_id, status, rank_order)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_rule_remediation_policy
  ON rule_remediation_mapping (trust_score_policy_id, status, rank_order)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_rule_remediation_decision
  ON rule_remediation_mapping (decision_action, status, rank_order)
  WHERE is_deleted = false;
