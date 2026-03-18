CREATE TABLE IF NOT EXISTS system_information_rule_condition (
  id BIGSERIAL PRIMARY KEY,
  system_information_rule_id BIGINT NOT NULL REFERENCES system_information_rule(id) ON DELETE CASCADE,

  condition_group SMALLINT NOT NULL DEFAULT 1 CHECK (condition_group >= 1),
  field_name TEXT NOT NULL,
  operator TEXT NOT NULL CHECK (operator IN ('EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NOT_IN', 'REGEX', 'EXISTS', 'NOT_EXISTS')),

  value_text TEXT,
  value_numeric NUMERIC(20, 6),
  value_boolean BOOLEAN,
  value_json JSONB,
  weight SMALLINT NOT NULL DEFAULT 1 CHECK (weight BETWEEN 1 AND 100),

  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL,

  CONSTRAINT ck_sys_rule_condition_value
    CHECK (
      operator IN ('EXISTS', 'NOT_EXISTS')
      OR value_text IS NOT NULL
      OR value_numeric IS NOT NULL
      OR value_boolean IS NOT NULL
      OR value_json IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_sysrule_condition_rule
  ON system_information_rule_condition (system_information_rule_id, condition_group)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sysrule_condition_field
  ON system_information_rule_condition (field_name, operator)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sysrule_condition_value_json
  ON system_information_rule_condition USING gin (value_json);
