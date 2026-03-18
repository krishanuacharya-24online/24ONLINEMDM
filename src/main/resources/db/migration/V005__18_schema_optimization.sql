-- Runtime optimization for existing databases:
-- 1) add guard constraints/indexes used by evaluator
-- 2) drop heavy or low-value indexes on high-write tables

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

DO $$
DECLARE
  idx TEXT;
BEGIN
  FOREACH idx IN ARRAY ARRAY[
    'idx_sysrule_active_eval',
    'idx_sysrule_targeting',
    'idx_sysrule_action',
    'idx_sysrule_flags',
    'idx_sysrule_vendor_trgm',
    'idx_posture_payload_json',
    'idx_trust_event_rule',
    'idx_trust_event_reject_app',
    'idx_trust_event_policy',
    'idx_trust_event_payload',
    'idx_device_installed_appname_trgm',
    'idx_device_installed_publisher_trgm',
    'idx_remediation_instruction_json',
    'idx_posture_eval_match_system_rule',
    'idx_posture_eval_match_reject_app',
    'idx_posture_eval_match_policy',
    'idx_posture_eval_match_remediation',
    'idx_posture_eval_match_detail',
    'idx_posture_eval_remediation_instruction',
    'idx_device_decision_response_payload'
  ]
  LOOP
    EXECUTE format('DROP INDEX IF EXISTS %I', idx);
  END LOOP;
END
$$;

