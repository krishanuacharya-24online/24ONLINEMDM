import { mountCrud } from '../crud.js';

document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('crud-root');
  if (!root) return;
  const nowIso = new Date().toISOString();

  mountCrud(root, {
    title: 'Rule remediation mappings',
    subtitle: 'Backed by rule_remediation_mapping. Provide exactly one target per source_type.',
    idField: 'id',
    listUrl: '/v1/policies/rule-remediation-mappings?size=200',
    createUrl: '/v1/policies/rule-remediation-mappings',
    updateUrl: (id) => `/v1/policies/rule-remediation-mappings/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/rule-remediation-mappings/${encodeURIComponent(id)}`,
    columns: [
      { name: 'id', label: 'ID' },
      { name: 'source_type', label: 'Source' },
      { name: 'system_information_rule_id', label: 'RuleId' },
      { name: 'reject_application_list_id', label: 'RejectId' },
      { name: 'trust_score_policy_id', label: 'PolicyId' },
      { name: 'remediation_rule_id', label: 'RemediationId' }
    ],
    fields: [
      { name: 'source_type', label: 'Source type', type: 'select', required: true, defaultValue: 'SYSTEM_RULE', options: ['SYSTEM_RULE', 'REJECT_APPLICATION', 'TRUST_POLICY', 'DECISION'] },
      { name: 'system_information_rule_id', label: 'System rule ID', type: 'number', placeholder: 'When source_type=SYSTEM_RULE' },
      { name: 'reject_application_list_id', label: 'Reject app ID', type: 'number', placeholder: 'When source_type=REJECT_APPLICATION' },
      { name: 'trust_score_policy_id', label: 'Trust policy ID', type: 'number', placeholder: 'When source_type=TRUST_POLICY' },
      { name: 'decision_action', label: 'Decision action', type: 'select', options: ['', 'ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY'], defaultValue: '' },
      { name: 'remediation_rule_id', label: 'Remediation rule ID', type: 'number', required: true },
      { name: 'enforce_mode', label: 'Enforce mode', type: 'select', required: true, defaultValue: 'ADVISORY', options: ['AUTO', 'MANUAL', 'ADVISORY'] },
      { name: 'rank_order', label: 'Rank order', type: 'number', required: true, defaultValue: 1 },
      { name: 'status', label: 'Status', type: 'select', required: true, defaultValue: 'ACTIVE', options: ['ACTIVE', 'INACTIVE'] },
      { name: 'effective_from', label: 'Effective from (ISO-8601)', required: true, defaultValue: nowIso, placeholder: '2026-02-27T10:00:00Z' },
      { name: 'effective_to', label: 'Effective to (ISO-8601)', placeholder: 'Optional' }
    ]
  });
});

