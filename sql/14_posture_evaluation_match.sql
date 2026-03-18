CREATE TABLE IF NOT EXISTS posture_evaluation_match (
  id BIGSERIAL PRIMARY KEY,

  posture_evaluation_run_id BIGINT NOT NULL REFERENCES posture_evaluation_run(id) ON DELETE CASCADE,
  match_source TEXT NOT NULL CHECK (match_source IN ('SYSTEM_RULE', 'REJECT_APPLICATION', 'TRUST_POLICY')),
  system_information_rule_id BIGINT REFERENCES system_information_rule(id),
  reject_application_list_id BIGINT REFERENCES reject_application_list(id),
  trust_score_policy_id BIGINT REFERENCES trust_score_policy(id),
  os_release_lifecycle_master_id BIGINT,
  os_lifecycle_state TEXT,

  device_installed_application_id BIGINT REFERENCES device_installed_application(id) ON DELETE SET NULL,
  remediation_rule_id BIGINT REFERENCES remediation_rule(id),

  matched BOOLEAN NOT NULL DEFAULT true,
  severity SMALLINT CHECK (severity IS NULL OR severity BETWEEN 1 AND 5),
  compliance_action TEXT CHECK (compliance_action IN ('ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY')),
  score_delta SMALLINT NOT NULL DEFAULT 0 CHECK (score_delta BETWEEN -1000 AND 1000),
  match_detail JSONB,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'rule-engine',

  CONSTRAINT ck_posture_eval_match_target
    CHECK (
      (match_source = 'SYSTEM_RULE'
        AND system_information_rule_id IS NOT NULL
        AND reject_application_list_id IS NULL
        AND trust_score_policy_id IS NULL)
      OR
      (match_source = 'REJECT_APPLICATION'
        AND system_information_rule_id IS NULL
        AND reject_application_list_id IS NOT NULL
        AND trust_score_policy_id IS NULL)
      OR
      (match_source = 'TRUST_POLICY'
        AND system_information_rule_id IS NULL
        AND reject_application_list_id IS NULL
        AND trust_score_policy_id IS NOT NULL)
    ),
  CONSTRAINT ck_eval_match_os_lifecycle_state
    CHECK (
      os_lifecycle_state IS NULL
      OR os_lifecycle_state IN ('SUPPORTED', 'EOL', 'EEOL', 'NOT_TRACKED')
    )
);

CREATE INDEX IF NOT EXISTS idx_posture_eval_match_run
  ON posture_evaluation_match (posture_evaluation_run_id);

CREATE INDEX IF NOT EXISTS idx_posture_eval_match_source
  ON posture_evaluation_match (match_source, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_posture_eval_match_os_lifecycle
  ON posture_evaluation_match (os_lifecycle_state, created_at DESC);
