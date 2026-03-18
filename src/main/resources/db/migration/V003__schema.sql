-- Consolidated Flyway migration for the remaining schema objects.
-- Source: sql/*.sql (excluding sql/00_apply_all.sql which contains psql-only commands).

-- 02_system_information_rule.sql
CREATE TABLE IF NOT EXISTS system_information_rule (
  id BIGSERIAL PRIMARY KEY,

  rule_code TEXT NOT NULL,

  priority INTEGER NOT NULL DEFAULT 100 CHECK (priority BETWEEN 1 AND 100000),
  version INTEGER NOT NULL DEFAULT 1 CHECK (version BETWEEN 1 AND 100000),

  match_mode TEXT NOT NULL CHECK (match_mode IN ('ALL', 'ANY')),
  compliance_action TEXT NOT NULL CHECK (compliance_action IN ('ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY')),
  risk_score_delta SMALLINT NOT NULL DEFAULT 0 CHECK (risk_score_delta BETWEEN -1000 AND 1000),

  rule_tag TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  severity SMALLINT NOT NULL CHECK (severity BETWEEN 1 AND 5),
  description TEXT,

  device_type TEXT CHECK (device_type IN ('PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER')),
  os_type TEXT NOT NULL CHECK (os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD')),
  os_name TEXT,
  os_version TEXT,
  time_zone TEXT,
  kernel_version TEXT,
  apiLevel INTEGER CHECK (apiLevel IS NULL OR apiLevel >= 0),
  osBuildNumber TEXT,
  manufacturer TEXT,

  rootDetected BOOLEAN,
  runningOnEmulator BOOLEAN,
  usb_debigging_status BOOLEAN,

  effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  effective_to TIMESTAMPTZ,
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL,

  CONSTRAINT ck_sysrule_os_name_linux
    CHECK (os_name IS NULL OR os_type = 'LINUX'),
  CONSTRAINT ck_sysrule_effective_window
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sys_rule_code
  ON system_information_rule (rule_code)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sysrule_tag_ver
  ON system_information_rule (os_type, os_name, device_type, rule_tag, version)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sysrule_eval_active
  ON system_information_rule (os_type, os_name, device_type, priority)
  WHERE status = 'ACTIVE' AND is_deleted = false;

-- 03_system_information_rule_condition.sql
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

-- 04_trust_score_policy.sql
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

CREATE UNIQUE INDEX IF NOT EXISTS uq_trust_policy_code
  ON trust_score_policy (policy_code)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_trust_policy_signal_active
  ON trust_score_policy (source_type, signal_key, COALESCE(severity, 0), COALESCE(compliance_action, ''))
  WHERE status = 'ACTIVE' AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_trust_policy_lookup
  ON trust_score_policy (source_type, signal_key, status, effective_from, effective_to)
  WHERE is_deleted = false;

-- 05_device_trust_profile.sql
CREATE TABLE IF NOT EXISTS device_trust_profile (
  id BIGSERIAL PRIMARY KEY,

  tenant_id TEXT,
  device_external_id TEXT NOT NULL,
  device_type TEXT CHECK (device_type IN ('PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER')),
  os_type TEXT NOT NULL CHECK (os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD')),
  os_name TEXT,
  os_lifecycle_state TEXT NOT NULL DEFAULT 'NOT_TRACKED',

  current_score SMALLINT NOT NULL DEFAULT 100 CHECK (current_score BETWEEN 0 AND 100),
  score_band TEXT NOT NULL DEFAULT 'TRUSTED' CHECK (score_band IN ('CRITICAL', 'HIGH_RISK', 'MEDIUM_RISK', 'LOW_RISK', 'TRUSTED')),
  posture_status TEXT NOT NULL DEFAULT 'COMPLIANT' CHECK (posture_status IN ('COMPLIANT', 'NON_COMPLIANT', 'UNKNOWN')),

  last_event_at TIMESTAMPTZ,
  last_recalculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL,

  CONSTRAINT ck_device_profile_os_name_linux
    CHECK (os_name IS NULL OR os_type = 'LINUX'),
  CONSTRAINT ck_device_profile_os_lifecycle_state
    CHECK (os_lifecycle_state IN ('SUPPORTED', 'EOL', 'EEOL', 'NOT_TRACKED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_trust_profile_device
  ON device_trust_profile (COALESCE(tenant_id, ''), device_external_id)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_device_trust_profile_score
  ON device_trust_profile (score_band, current_score);

CREATE INDEX IF NOT EXISTS idx_device_trust_profile_target
  ON device_trust_profile (os_type, os_name, device_type);

CREATE INDEX IF NOT EXISTS idx_device_trust_profile_lifecycle
  ON device_trust_profile (os_lifecycle_state, current_score);

-- 06_device_trust_score_event.sql
CREATE TABLE IF NOT EXISTS device_trust_score_event (
  id BIGSERIAL PRIMARY KEY,

  device_trust_profile_id BIGINT NOT NULL REFERENCES device_trust_profile(id) ON DELETE CASCADE,
  event_source TEXT NOT NULL CHECK (event_source IN ('SYSTEM_RULE', 'REJECT_APPLICATION', 'POSTURE_SIGNAL', 'MANUAL')),
  source_record_id BIGINT,

  trust_score_policy_id BIGINT REFERENCES trust_score_policy(id),
  system_information_rule_id BIGINT REFERENCES system_information_rule(id),
  reject_application_list_id BIGINT REFERENCES reject_application_list(id),
  os_release_lifecycle_master_id BIGINT,
  os_lifecycle_state TEXT,

  observed_payload JSONB,
  score_before SMALLINT NOT NULL CHECK (score_before BETWEEN 0 AND 100),
  score_delta SMALLINT NOT NULL CHECK (score_delta BETWEEN -1000 AND 1000),
  score_after SMALLINT NOT NULL CHECK (score_after BETWEEN 0 AND 100),

  event_time TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_by TEXT NOT NULL,
  notes TEXT,

  CONSTRAINT ck_trust_event_score_math
    CHECK (score_after = LEAST(100, GREATEST(0, score_before + score_delta))),
  CONSTRAINT ck_trust_event_os_lifecycle_state
    CHECK (
      os_lifecycle_state IS NULL
      OR os_lifecycle_state IN ('SUPPORTED', 'EOL', 'EEOL', 'NOT_TRACKED')
    )
);

CREATE INDEX IF NOT EXISTS idx_trust_event_profile_time
  ON device_trust_score_event (device_trust_profile_id, event_time DESC);

CREATE INDEX IF NOT EXISTS idx_trust_event_source
  ON device_trust_score_event (event_source, event_time DESC);

CREATE INDEX IF NOT EXISTS idx_trust_event_os_lifecycle
  ON device_trust_score_event (os_release_lifecycle_master_id, event_time DESC);

-- 07_device_posture_payload.sql
CREATE TABLE IF NOT EXISTS device_posture_payload (
  id BIGSERIAL PRIMARY KEY,

  tenant_id TEXT,
  device_external_id TEXT NOT NULL,
  agent_id TEXT,
  payload_version TEXT,
  payload_hash TEXT,
  payload_json JSONB NOT NULL,

  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  process_status TEXT NOT NULL DEFAULT 'RECEIVED'
    CHECK (process_status IN ('RECEIVED', 'VALIDATED', 'EVALUATED', 'FAILED')),
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

-- 08_device_system_snapshot.sql
CREATE TABLE IF NOT EXISTS device_system_snapshot (
  id BIGSERIAL PRIMARY KEY,

  device_posture_payload_id BIGINT NOT NULL REFERENCES device_posture_payload(id) ON DELETE CASCADE,
  device_trust_profile_id BIGINT REFERENCES device_trust_profile(id) ON DELETE SET NULL,

  capture_time TIMESTAMPTZ NOT NULL DEFAULT now(),
  device_type TEXT CHECK (device_type IN ('PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER')),
  os_type TEXT NOT NULL CHECK (os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD')),
  os_name TEXT,
  os_cycle TEXT,
  os_release_lifecycle_master_id BIGINT,
  os_version TEXT,
  time_zone TEXT,
  kernel_version TEXT,
  api_level INTEGER CHECK (api_level IS NULL OR api_level >= 0),
  os_build_number TEXT,
  manufacturer TEXT,

  root_detected BOOLEAN,
  running_on_emulator BOOLEAN,
  usb_debugging_status BOOLEAN,
  is_latest BOOLEAN NOT NULL DEFAULT true,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'posture-parser',

  CONSTRAINT ck_snapshot_os_name_linux
    CHECK (os_name IS NULL OR os_type = 'LINUX')
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_system_snapshot_payload
  ON device_system_snapshot (device_posture_payload_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_system_snapshot_latest
  ON device_system_snapshot (device_trust_profile_id)
  WHERE is_latest = true AND device_trust_profile_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_device_system_snapshot_profile_time
  ON device_system_snapshot (device_trust_profile_id, capture_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_system_snapshot_targeting
  ON device_system_snapshot (os_type, os_name, os_cycle, device_type, capture_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_system_snapshot_lifecycle
  ON device_system_snapshot (os_release_lifecycle_master_id, capture_time DESC);

-- 09_device_installed_application.sql
CREATE TABLE IF NOT EXISTS device_installed_application (
  id BIGSERIAL PRIMARY KEY,

  device_posture_payload_id BIGINT NOT NULL REFERENCES device_posture_payload(id) ON DELETE CASCADE,
  device_trust_profile_id BIGINT REFERENCES device_trust_profile(id) ON DELETE SET NULL,
  capture_time TIMESTAMPTZ NOT NULL DEFAULT now(),

  app_name TEXT NOT NULL,
  publisher TEXT,
  package_id TEXT,
  app_os_type TEXT NOT NULL CHECK (app_os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX')),
  app_version TEXT,
  latest_available_version TEXT,
  is_system_app BOOLEAN,
  install_source TEXT,
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED', 'UNKNOWN')),

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'posture-parser'
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_device_installed_app_payload_identity
  ON device_installed_application (
    device_posture_payload_id,
    app_os_type,
    COALESCE(package_id, ''),
    lower(app_name)
  );

CREATE INDEX IF NOT EXISTS idx_device_installed_app_profile_time
  ON device_installed_application (device_trust_profile_id, capture_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_installed_app_lookup
  ON device_installed_application (app_os_type, COALESCE(package_id, ''), lower(app_name));

-- 10_remediation_rule.sql
CREATE TABLE IF NOT EXISTS remediation_rule (
  id BIGSERIAL PRIMARY KEY,

  remediation_code TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  remediation_type TEXT NOT NULL
    CHECK (remediation_type IN ('USER_ACTION', 'AUTO_ACTION', 'NETWORK_RESTRICT', 'APP_REMOVAL', 'OS_UPDATE', 'POLICY_ACK')),

  os_type TEXT CHECK (os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX')),
  device_type TEXT CHECK (device_type IN ('PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER')),
  instruction_json JSONB NOT NULL DEFAULT '{}'::jsonb,

  priority SMALLINT NOT NULL DEFAULT 100 CHECK (priority BETWEEN 1 AND 1000),
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  effective_to TIMESTAMPTZ,
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL,

  CONSTRAINT ck_remediation_effective_window
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_remediation_code
  ON remediation_rule (remediation_code)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_remediation_targeting
  ON remediation_rule (os_type, device_type, status, priority)
  WHERE is_deleted = false;

-- 11_rule_remediation_mapping.sql
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

-- 12_trust_score_decision_policy.sql
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

-- 13_posture_evaluation_run.sql
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

-- 14_posture_evaluation_match.sql
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

-- 15_posture_evaluation_remediation.sql
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

-- 16_device_decision_response.sql
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

-- 17_link_trust_event_run.sql
ALTER TABLE device_trust_score_event
  ADD COLUMN IF NOT EXISTS posture_evaluation_run_id BIGINT
  REFERENCES posture_evaluation_run(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_trust_event_eval_run
  ON device_trust_score_event (posture_evaluation_run_id, event_time DESC);

-- 19_normalization_lookup_and_catalog.sql
CREATE TEMP TABLE IF NOT EXISTS _mdm_lookup_seed (
  table_name TEXT NOT NULL,
  code TEXT NOT NULL,
  description TEXT NOT NULL
);

TRUNCATE TABLE _mdm_lookup_seed;

INSERT INTO _mdm_lookup_seed (table_name, code, description)
VALUES
  ('lkp_os_type', 'ANDROID', 'Android operating system'),
  ('lkp_os_type', 'IOS', 'Apple iOS operating system'),
  ('lkp_os_type', 'WINDOWS', 'Microsoft Windows operating system'),
  ('lkp_os_type', 'MACOS', 'Apple macOS operating system'),
  ('lkp_os_type', 'LINUX', 'Linux operating system'),
  ('lkp_os_type', 'CHROMEOS', 'Google ChromeOS operating system'),
  ('lkp_os_type', 'FREEBSD', 'FreeBSD operating system'),
  ('lkp_os_type', 'OPENBSD', 'OpenBSD operating system'),
  ('lkp_os_name', 'DEBIAN', 'Debian Linux distribution'),
  ('lkp_os_name', 'CENTOS', 'CentOS Linux distribution'),
  ('lkp_os_name', 'FEDORA', 'Fedora Linux distribution'),
  ('lkp_os_name', 'MINT', 'Linux Mint distribution'),
  ('lkp_os_name', 'LINUXMINT', 'Linux Mint distribution (canonical code)'),
  ('lkp_os_name', 'REDHAT', 'Red Hat Enterprise Linux distribution'),
  ('lkp_os_name', 'RHEL', 'Red Hat Enterprise Linux distribution (canonical code)'),
  ('lkp_os_name', 'UBUNTU', 'Ubuntu Linux distribution'),
  ('lkp_os_name', 'ROCKY', 'Rocky Linux distribution'),
  ('lkp_os_name', 'ALMALINUX', 'AlmaLinux distribution'),
  ('lkp_os_name', 'SUSE', 'SUSE Linux distribution'),
  ('lkp_os_name', 'OPENSUSE', 'openSUSE Linux distribution'),
  ('lkp_os_name', 'ARCH', 'Arch Linux distribution'),
  ('lkp_os_name', 'KALI', 'Kali Linux distribution'),
  ('lkp_os_name', 'OTHER', 'Other Linux distribution'),
  ('lkp_os_platform', 'WINDOWS', 'Microsoft Windows platform'),
  ('lkp_os_platform', 'MACOS', 'Apple macOS platform'),
  ('lkp_os_platform', 'CHROMEOS', 'Google ChromeOS platform'),
  ('lkp_os_platform', 'UBUNTU', 'Ubuntu platform'),
  ('lkp_os_platform', 'DEBIAN', 'Debian platform'),
  ('lkp_os_platform', 'FEDORA', 'Fedora platform'),
  ('lkp_os_platform', 'ARCH', 'Arch Linux platform'),
  ('lkp_os_platform', 'OPENSUSE', 'openSUSE platform'),
  ('lkp_os_platform', 'LINUXMINT', 'Linux Mint platform'),
  ('lkp_os_platform', 'KALI', 'Kali Linux platform'),
  ('lkp_os_platform', 'ANDROID', 'Android platform'),
  ('lkp_os_platform', 'IOS', 'Apple iOS platform'),
  ('lkp_os_platform', 'RHEL', 'Red Hat Enterprise Linux platform'),
  ('lkp_os_platform', 'CENTOS', 'CentOS platform'),
  ('lkp_os_platform', 'ROCKY', 'Rocky Linux platform'),
  ('lkp_os_platform', 'ALMALINUX', 'AlmaLinux platform'),
  ('lkp_os_platform', 'FREEBSD', 'FreeBSD platform'),
  ('lkp_os_platform', 'OPENBSD', 'OpenBSD platform'),
  ('lkp_os_lifecycle_state', 'SUPPORTED', 'Vendor still supports this cycle with no announced EOL date'),
  ('lkp_os_lifecycle_state', 'EOL', 'Cycle has crossed EOL date but may still be in extended support'),
  ('lkp_os_lifecycle_state', 'EEOL', 'Cycle has crossed extended EOL date or final support date'),
  ('lkp_os_lifecycle_state', 'NOT_TRACKED', 'Lifecycle cannot be resolved from catalog'),
  ('lkp_device_type', 'PHONE', 'Phone device class'),
  ('lkp_device_type', 'TABLET', 'Tablet device class'),
  ('lkp_device_type', 'LAPTOP', 'Laptop device class'),
  ('lkp_device_type', 'DESKTOP', 'Desktop device class'),
  ('lkp_device_type', 'IOT', 'Internet of Things device class'),
  ('lkp_device_type', 'SERVER', 'Server device class'),
  ('lkp_record_status', 'ACTIVE', 'Record is active'),
  ('lkp_record_status', 'INACTIVE', 'Record is inactive'),
  ('lkp_match_mode', 'ALL', 'All conditions must match'),
  ('lkp_match_mode', 'ANY', 'Any one condition can match'),
  ('lkp_compliance_action', 'ALLOW', 'Allow normal access'),
  ('lkp_compliance_action', 'NOTIFY', 'Allow with notification'),
  ('lkp_compliance_action', 'QUARANTINE', 'Limit device/network access'),
  ('lkp_compliance_action', 'BLOCK', 'Block access'),
  ('lkp_rule_condition_operator', 'EQ', 'Equals'),
  ('lkp_rule_condition_operator', 'NEQ', 'Not equals'),
  ('lkp_rule_condition_operator', 'GT', 'Greater than'),
  ('lkp_rule_condition_operator', 'GTE', 'Greater than or equal'),
  ('lkp_rule_condition_operator', 'LT', 'Less than'),
  ('lkp_rule_condition_operator', 'LTE', 'Less than or equal'),
  ('lkp_rule_condition_operator', 'IN', 'Contained in set'),
  ('lkp_rule_condition_operator', 'NOT_IN', 'Not contained in set'),
  ('lkp_rule_condition_operator', 'REGEX', 'Regular expression match'),
  ('lkp_rule_condition_operator', 'EXISTS', 'Field exists'),
  ('lkp_rule_condition_operator', 'NOT_EXISTS', 'Field does not exist'),
  ('lkp_signal_source', 'SYSTEM_RULE', 'Signal generated from system-information rule'),
  ('lkp_signal_source', 'REJECT_APPLICATION', 'Signal generated from reject-application policy'),
  ('lkp_signal_source', 'POSTURE_SIGNAL', 'Signal generated from posture detection'),
  ('lkp_signal_source', 'MANUAL', 'Signal generated manually by operator'),
  ('lkp_score_band', 'CRITICAL', 'Critical trust state'),
  ('lkp_score_band', 'HIGH_RISK', 'High risk state'),
  ('lkp_score_band', 'MEDIUM_RISK', 'Medium risk state'),
  ('lkp_score_band', 'LOW_RISK', 'Low risk state'),
  ('lkp_score_band', 'TRUSTED', 'Trusted state'),
  ('lkp_posture_status', 'COMPLIANT', 'Device is compliant'),
  ('lkp_posture_status', 'NON_COMPLIANT', 'Device is non-compliant'),
  ('lkp_posture_status', 'UNKNOWN', 'Compliance state is unknown'),
  ('lkp_payload_process_status', 'RECEIVED', 'Payload received from device'),
  ('lkp_payload_process_status', 'VALIDATED', 'Payload validated'),
  ('lkp_payload_process_status', 'EVALUATED', 'Payload evaluated'),
  ('lkp_payload_process_status', 'FAILED', 'Payload processing failed'),
  ('lkp_installed_app_status', 'ACTIVE', 'Application currently installed and active'),
  ('lkp_installed_app_status', 'REMOVED', 'Application was removed'),
  ('lkp_installed_app_status', 'UNKNOWN', 'Application state is unknown'),
  ('lkp_remediation_type', 'USER_ACTION', 'User must perform a remediation step'),
  ('lkp_remediation_type', 'AUTO_ACTION', 'System performs remediation automatically'),
  ('lkp_remediation_type', 'NETWORK_RESTRICT', 'Network access restriction remediation'),
  ('lkp_remediation_type', 'APP_REMOVAL', 'Remove a disallowed application'),
  ('lkp_remediation_type', 'OS_UPDATE', 'Update operating system'),
  ('lkp_remediation_type', 'POLICY_ACK', 'User policy acknowledgment required'),
  ('lkp_rule_remediation_source', 'SYSTEM_RULE', 'Mapping from system-information rule'),
  ('lkp_rule_remediation_source', 'REJECT_APPLICATION', 'Mapping from reject-application policy'),
  ('lkp_rule_remediation_source', 'TRUST_POLICY', 'Mapping from trust-score policy'),
  ('lkp_rule_remediation_source', 'DECISION', 'Mapping from final decision action'),
  ('lkp_enforce_mode', 'AUTO', 'Automatically enforce remediation'),
  ('lkp_enforce_mode', 'MANUAL', 'Manual enforcement by operator'),
  ('lkp_enforce_mode', 'ADVISORY', 'Advisory guidance only'),
  ('lkp_evaluation_status', 'IN_PROGRESS', 'Evaluation in progress'),
  ('lkp_evaluation_status', 'COMPLETED', 'Evaluation completed'),
  ('lkp_evaluation_status', 'FAILED', 'Evaluation failed'),
  ('lkp_match_source', 'SYSTEM_RULE', 'Match from system-information rule'),
  ('lkp_match_source', 'REJECT_APPLICATION', 'Match from reject-application policy'),
  ('lkp_match_source', 'TRUST_POLICY', 'Match from trust-score policy'),
  ('lkp_remediation_source', 'MATCH', 'Remediation generated from matched finding'),
  ('lkp_remediation_source', 'DECISION', 'Remediation generated from final decision'),
  ('lkp_remediation_status', 'PENDING', 'Remediation pending'),
  ('lkp_remediation_status', 'SENT', 'Remediation sent'),
  ('lkp_remediation_status', 'ACKED', 'Remediation acknowledged'),
  ('lkp_remediation_status', 'SKIPPED', 'Remediation skipped'),
  ('lkp_remediation_status', 'FAILED', 'Remediation failed'),
  ('lkp_delivery_status', 'PENDING', 'Delivery pending'),
  ('lkp_delivery_status', 'SENT', 'Delivery sent'),
  ('lkp_delivery_status', 'ACKED', 'Delivery acknowledged'),
  ('lkp_delivery_status', 'FAILED', 'Delivery failed'),
  ('lkp_delivery_status', 'TIMEOUT', 'Delivery timed out'),
  ('lkp_app_category', 'VPN_PROXY', 'VPN and proxy applications'),
  ('lkp_app_category', 'ROOT_PRIVILEGE', 'Root or privilege escalation tools'),
  ('lkp_app_category', 'REMOTE_ADMIN', 'Remote administration applications'),
  ('lkp_app_category', 'SIDELOAD_STORE', 'Third-party stores and sideload sources'),
  ('lkp_app_category', 'P2P', 'Peer-to-peer and torrent applications'),
  ('lkp_app_category', 'SPYWARE', 'Spyware applications'),
  ('lkp_app_category', 'ADWARE', 'Adware applications'),
  ('lkp_app_category', 'FAKE_OPTIMIZER', 'Fake optimizer/scam applications'),
  ('lkp_app_category', 'CRYPTO_MINING', 'Cryptomining applications'),
  ('lkp_app_category', 'CHEAT_TOOLS', 'Game cheat and hacking tools'),
  ('lkp_threat_type', 'VPN', 'Virtual private network risk'),
  ('lkp_threat_type', 'ROOT', 'Privilege escalation risk'),
  ('lkp_threat_type', 'RAT', 'Remote administration tool risk'),
  ('lkp_threat_type', 'SIDELOAD', 'Sideload source risk'),
  ('lkp_threat_type', 'TORRENT', 'Peer-to-peer/torrent risk'),
  ('lkp_threat_type', 'SPYWARE', 'Spyware risk'),
  ('lkp_threat_type', 'ADWARE', 'Adware risk'),
  ('lkp_threat_type', 'SCAM', 'Fraud/scam risk'),
  ('lkp_threat_type', 'MINER', 'Cryptomining risk'),
  ('lkp_threat_type', 'CHEAT', 'Cheat/hack risk');

CREATE TABLE IF NOT EXISTS lkp_master (
  lookup_type TEXT NOT NULL,
  code TEXT NOT NULL,
  description TEXT NOT NULL,
  PRIMARY KEY (lookup_type, code)
);

CREATE INDEX IF NOT EXISTS idx_lkp_master_code
  ON lkp_master (code);

INSERT INTO lkp_master (lookup_type, code, description)
SELECT
  s.table_name,
  s.code,
  s.description
FROM _mdm_lookup_seed s
ON CONFLICT (lookup_type, code) DO UPDATE
SET description = EXCLUDED.description;

DELETE FROM lkp_master m
WHERE NOT EXISTS (
  SELECT 1
  FROM _mdm_lookup_seed s
  WHERE s.table_name = m.lookup_type
    AND s.code = m.code
);

DO $$
DECLARE
  rec RECORD;
BEGIN
  FOR rec IN
    SELECT t.tablename
    FROM pg_catalog.pg_tables t
    WHERE t.schemaname = 'public'
      AND t.tablename LIKE 'lkp\_%' ESCAPE '\'
      AND t.tablename <> 'lkp_master'
  LOOP
    EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', rec.tablename);
  END LOOP;
END
$$;

CREATE TABLE IF NOT EXISTS application_catalog (
  id BIGSERIAL PRIMARY KEY,
  os_type TEXT NOT NULL,
  package_id TEXT,
  app_name TEXT NOT NULL,
  app_name_norm TEXT GENERATED ALWAYS AS (lower(app_name)) STORED,
  package_id_norm TEXT GENERATED ALWAYS AS (COALESCE(package_id, '')) STORED,
  publisher TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_application_catalog_identity
  ON application_catalog (os_type, package_id_norm, app_name_norm);

CREATE INDEX IF NOT EXISTS idx_application_catalog_lookup
  ON application_catalog (os_type, package_id_norm, app_name_norm);

DROP TABLE IF EXISTS _mdm_lookup_seed;

-- 20_normalization_fk_migration.sql
INSERT INTO lkp_master (lookup_type, code, description)
SELECT DISTINCT 'lkp_app_category', app_category, app_category
FROM reject_application_list
WHERE app_category IS NOT NULL
ON CONFLICT (lookup_type, code) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO lkp_master (lookup_type, code, description)
SELECT DISTINCT 'lkp_threat_type', threat_type, threat_type
FROM reject_application_list
WHERE threat_type IS NOT NULL
ON CONFLICT (lookup_type, code) DO UPDATE
SET description = EXCLUDED.description;

ALTER TABLE reject_application_list
  ADD COLUMN IF NOT EXISTS application_catalog_id BIGINT;

ALTER TABLE device_installed_application
  ADD COLUMN IF NOT EXISTS application_catalog_id BIGINT;

ALTER TABLE system_information_rule
  ADD COLUMN IF NOT EXISTS os_name TEXT;

ALTER TABLE device_system_snapshot
  ADD COLUMN IF NOT EXISTS os_name TEXT;

ALTER TABLE device_trust_profile
  ADD COLUMN IF NOT EXISTS os_name TEXT;

DO $$
DECLARE
  rec RECORD;
BEGIN
  FOR rec IN
    SELECT *
    FROM (
      VALUES
        ('system_information_rule', 'ck_sysrule_os_name_linux', 'CHECK (os_name IS NULL OR os_type = ''LINUX'')'),
        ('device_system_snapshot', 'ck_snapshot_os_name_linux', 'CHECK (os_name IS NULL OR os_type = ''LINUX'')'),
        ('device_trust_profile', 'ck_device_profile_os_name_linux', 'CHECK (os_name IS NULL OR os_type = ''LINUX'')')
    ) AS v(table_name, constraint_name, constraint_sql)
  LOOP
    IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint c
      WHERE c.conname = rec.constraint_name
        AND c.conrelid = to_regclass(rec.table_name)
    ) THEN
      EXECUTE format(
        'ALTER TABLE %I ADD CONSTRAINT %I %s',
        rec.table_name,
        rec.constraint_name,
        rec.constraint_sql
      );
    END IF;
  END LOOP;
END
$$;

INSERT INTO application_catalog (os_type, package_id, app_name, publisher)
SELECT DISTINCT
  r.app_os_type,
  NULLIF(r.package_id, ''),
  r.app_name,
  NULLIF(r.publisher, '')
FROM reject_application_list r
ON CONFLICT (os_type, package_id_norm, app_name_norm) DO UPDATE
SET
  publisher = COALESCE(application_catalog.publisher, EXCLUDED.publisher),
  modified_at = now();

INSERT INTO application_catalog (os_type, package_id, app_name, publisher)
SELECT DISTINCT
  a.app_os_type,
  NULLIF(a.package_id, ''),
  a.app_name,
  NULLIF(a.publisher, '')
FROM device_installed_application a
ON CONFLICT (os_type, package_id_norm, app_name_norm) DO UPDATE
SET
  publisher = COALESCE(application_catalog.publisher, EXCLUDED.publisher),
  modified_at = now();

UPDATE reject_application_list r
SET application_catalog_id = c.id
FROM application_catalog c
WHERE r.application_catalog_id IS NULL
  AND c.os_type = r.app_os_type
  AND c.package_id_norm = COALESCE(r.package_id, '')
  AND c.app_name_norm = lower(r.app_name);

UPDATE device_installed_application a
SET application_catalog_id = c.id
FROM application_catalog c
WHERE a.application_catalog_id IS NULL
  AND c.os_type = a.app_os_type
  AND c.package_id_norm = COALESCE(a.package_id, '')
  AND c.app_name_norm = lower(a.app_name);

CREATE INDEX IF NOT EXISTS idx_reject_application_catalog_id
  ON reject_application_list (application_catalog_id);

CREATE INDEX IF NOT EXISTS idx_installed_application_catalog_id
  ON device_installed_application (application_catalog_id);

DO $$
DECLARE
  fk RECORD;
BEGIN
  FOR fk IN
    SELECT *
    FROM (
      VALUES
        ('reject_application_list', 'fk_reject_application_catalog', 'FOREIGN KEY (application_catalog_id) REFERENCES application_catalog(id)'),
        ('device_installed_application', 'fk_installed_application_catalog', 'FOREIGN KEY (application_catalog_id) REFERENCES application_catalog(id)')
    ) AS v(table_name, constraint_name, constraint_sql)
  LOOP
    IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint c
      WHERE c.conname = fk.constraint_name
        AND c.conrelid = to_regclass(fk.table_name)
    ) THEN
      EXECUTE format(
        'ALTER TABLE %I ADD CONSTRAINT %I %s',
        fk.table_name,
        fk.constraint_name,
        fk.constraint_sql
      );
    END IF;
  END LOOP;
END
$$;

CREATE OR REPLACE FUNCTION mdm_assign_application_catalog_id()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.application_catalog_id IS NULL THEN
    INSERT INTO application_catalog (os_type, package_id, app_name, publisher)
    VALUES (
      NEW.app_os_type,
      NULLIF(NEW.package_id, ''),
      NEW.app_name,
      NULLIF(NEW.publisher, '')
    )
    ON CONFLICT (os_type, package_id_norm, app_name_norm) DO UPDATE
    SET
      publisher = COALESCE(application_catalog.publisher, EXCLUDED.publisher),
      modified_at = now()
    RETURNING id INTO NEW.application_catalog_id;
  END IF;

  RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS trg_reject_assign_application_catalog ON reject_application_list;
CREATE TRIGGER trg_reject_assign_application_catalog
BEFORE INSERT OR UPDATE OF app_os_type, package_id, app_name, publisher, application_catalog_id
ON reject_application_list
FOR EACH ROW
EXECUTE FUNCTION mdm_assign_application_catalog_id();

DROP TRIGGER IF EXISTS trg_installed_assign_application_catalog ON device_installed_application;
CREATE TRIGGER trg_installed_assign_application_catalog
BEFORE INSERT OR UPDATE OF app_os_type, package_id, app_name, publisher, application_catalog_id
ON device_installed_application
FOR EACH ROW
EXECUTE FUNCTION mdm_assign_application_catalog_id();

-- 21_os_release_lifecycle_master.sql (partial; the remainder continues below)
CREATE TABLE IF NOT EXISTS os_release_lifecycle_master (
  id BIGSERIAL PRIMARY KEY,

  platform_code TEXT NOT NULL,
  os_type TEXT,
  os_name TEXT,
  cycle TEXT NOT NULL,

  released_on DATE,
  eol_on DATE,
  eeol_on DATE,
  latest_version TEXT,

  support_state TEXT NOT NULL DEFAULT 'TRACKED'
    CHECK (support_state IN ('TRACKED', 'SUPPORTED', 'NOT_FOUND')),
  source_name TEXT NOT NULL DEFAULT 'endoflife.date',
  source_url TEXT,
  notes TEXT,

  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL DEFAULT 'system-seed',
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL DEFAULT 'system-seed',

  CONSTRAINT uq_os_release_lifecycle UNIQUE (platform_code, cycle),
  CONSTRAINT ck_os_lifecycle_cycle_nonblank CHECK (length(trim(cycle)) > 0),
  CONSTRAINT ck_os_lifecycle_linux_name CHECK (os_name IS NULL OR os_type = 'LINUX'),
  CONSTRAINT ck_os_lifecycle_eol_vs_release CHECK (eol_on IS NULL OR released_on IS NULL OR eol_on >= released_on),
  CONSTRAINT ck_os_lifecycle_eeol_requires_eol CHECK (eeol_on IS NULL OR eol_on IS NOT NULL),
  CONSTRAINT ck_os_lifecycle_eeol_order CHECK (eeol_on IS NULL OR eol_on IS NULL OR eeol_on >= eol_on)
);

CREATE INDEX IF NOT EXISTS idx_os_lifecycle_master_lookup
  ON os_release_lifecycle_master (platform_code, support_state, eol_on, eeol_on);

CREATE INDEX IF NOT EXISTS idx_os_lifecycle_master_mapping
  ON os_release_lifecycle_master (os_type, os_name, cycle);

-- NOTE: the full lifecycle seed dataset is large; keep using the original sql/21_os_release_lifecycle_master.sql
-- and paste the remaining statements here if you want Flyway to seed it via migrations.

-- 18_schema_optimization.sql (already safe SQL)
DO $$
DECLARE
  rec RECORD;
BEGIN
  FOR rec IN
    SELECT *
    FROM (
      VALUES
        ('reject_application_list', 'ck_reject_effective_window', 'CHECK (effective_to IS NULL OR effective_to > effective_from)'),
        ('system_information_rule', 'ck_sysrule_effective_window', 'CHECK (effective_to IS NULL OR effective_to > effective_from)')
    ) AS v(table_name, constraint_name, constraint_sql)
  LOOP
    IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint c
      WHERE c.conname = rec.constraint_name
        AND c.conrelid = to_regclass(rec.table_name)
    ) THEN
      EXECUTE format(
        'ALTER TABLE %I ADD CONSTRAINT %I %s',
        rec.table_name,
        rec.constraint_name,
        rec.constraint_sql
      );
    END IF;
  END LOOP;
END
$$;

CREATE INDEX IF NOT EXISTS idx_reject_match_active
  ON reject_application_list (app_os_type, COALESCE(package_id, ''), lower(app_name))
  WHERE status = 'ACTIVE' AND is_deleted = false;

DROP INDEX IF EXISTS idx_sysrule_eval_active;

CREATE INDEX IF NOT EXISTS idx_sysrule_eval_active
  ON system_information_rule (os_type, os_name, device_type, priority)
  WHERE status = 'ACTIVE' AND is_deleted = false;

DROP INDEX IF EXISTS idx_device_system_snapshot_targeting;
CREATE INDEX IF NOT EXISTS idx_device_system_snapshot_targeting
  ON device_system_snapshot (os_type, os_name, os_cycle, device_type, capture_time DESC);

DROP INDEX IF EXISTS idx_device_trust_profile_target;
CREATE INDEX IF NOT EXISTS idx_device_trust_profile_target
  ON device_trust_profile (os_type, os_name, device_type);

DROP INDEX IF EXISTS uq_trust_policy_signal_active;

CREATE INDEX IF NOT EXISTS idx_trust_policy_signal_active
  ON trust_score_policy (source_type, signal_key, COALESCE(severity, 0), COALESCE(compliance_action, ''))
  WHERE status = 'ACTIVE' AND is_deleted = false;

