-- Support both global (tenant_id IS NULL) and tenant-scoped policy records.

ALTER TABLE system_information_rule
    ADD COLUMN IF NOT EXISTS tenant_id TEXT;

ALTER TABLE reject_application_list
    ADD COLUMN IF NOT EXISTS tenant_id TEXT;

ALTER TABLE trust_score_policy
    ADD COLUMN IF NOT EXISTS tenant_id TEXT;

ALTER TABLE trust_score_decision_policy
    ADD COLUMN IF NOT EXISTS tenant_id TEXT;

ALTER TABLE remediation_rule
    ADD COLUMN IF NOT EXISTS tenant_id TEXT;

ALTER TABLE rule_remediation_mapping
    ADD COLUMN IF NOT EXISTS tenant_id TEXT;

UPDATE system_information_rule SET tenant_id = NULL WHERE tenant_id IS NOT NULL AND btrim(tenant_id) = '';
UPDATE reject_application_list SET tenant_id = NULL WHERE tenant_id IS NOT NULL AND btrim(tenant_id) = '';
UPDATE trust_score_policy SET tenant_id = NULL WHERE tenant_id IS NOT NULL AND btrim(tenant_id) = '';
UPDATE trust_score_decision_policy SET tenant_id = NULL WHERE tenant_id IS NOT NULL AND btrim(tenant_id) = '';
UPDATE remediation_rule SET tenant_id = NULL WHERE tenant_id IS NOT NULL AND btrim(tenant_id) = '';
UPDATE rule_remediation_mapping SET tenant_id = NULL WHERE tenant_id IS NOT NULL AND btrim(tenant_id) = '';

UPDATE system_information_rule SET tenant_id = lower(tenant_id) WHERE tenant_id IS NOT NULL;
UPDATE reject_application_list SET tenant_id = lower(tenant_id) WHERE tenant_id IS NOT NULL;
UPDATE trust_score_policy SET tenant_id = lower(tenant_id) WHERE tenant_id IS NOT NULL;
UPDATE trust_score_decision_policy SET tenant_id = lower(tenant_id) WHERE tenant_id IS NOT NULL;
UPDATE remediation_rule SET tenant_id = lower(tenant_id) WHERE tenant_id IS NOT NULL;
UPDATE rule_remediation_mapping SET tenant_id = lower(tenant_id) WHERE tenant_id IS NOT NULL;

DROP INDEX IF EXISTS uq_sys_rule_code;
DROP INDEX IF EXISTS uq_reject_app_expr;
DROP INDEX IF EXISTS uq_trust_policy_code;
DROP INDEX IF EXISTS uq_trust_score_decision_policy;
DROP INDEX IF EXISTS uq_remediation_code;
DROP INDEX IF EXISTS uq_rule_remediation_mapping;

CREATE UNIQUE INDEX IF NOT EXISTS uq_sys_rule_code
    ON system_information_rule (COALESCE(tenant_id, ''), rule_code)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_reject_app_expr
    ON reject_application_list (COALESCE(tenant_id, ''), app_os_type, COALESCE(package_id, ''), lower(app_name), policy_tag)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_trust_policy_code
    ON trust_score_policy (COALESCE(tenant_id, ''), policy_code)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_trust_score_decision_policy
    ON trust_score_decision_policy (COALESCE(tenant_id, ''), policy_name, score_min, score_max, decision_action)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_remediation_code
    ON remediation_rule (COALESCE(tenant_id, ''), remediation_code)
    WHERE is_deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_rule_remediation_mapping
    ON rule_remediation_mapping (
        COALESCE(tenant_id, ''),
        source_type,
        COALESCE(system_information_rule_id, 0),
        COALESCE(reject_application_list_id, 0),
        COALESCE(trust_score_policy_id, 0),
        COALESCE(decision_action, ''),
        remediation_rule_id,
        rank_order
    )
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_sysrule_scope
    ON system_information_rule (COALESCE(tenant_id, ''), status, effective_from, effective_to)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_reject_scope
    ON reject_application_list (COALESCE(tenant_id, ''), status, effective_from, effective_to)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_trust_policy_scope
    ON trust_score_policy (COALESCE(tenant_id, ''), status, source_type, signal_key, effective_from, effective_to)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_trust_decision_scope
    ON trust_score_decision_policy (COALESCE(tenant_id, ''), status, score_min, score_max, effective_from, effective_to)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_remediation_scope
    ON remediation_rule (COALESCE(tenant_id, ''), status, effective_from, effective_to)
    WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_rule_remediation_scope
    ON rule_remediation_mapping (COALESCE(tenant_id, ''), source_type, status, rank_order, effective_from, effective_to)
    WHERE is_deleted = false;
