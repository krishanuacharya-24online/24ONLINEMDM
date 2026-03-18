import { mountCrud } from '../crud.js';

document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('crud-root');
  if (!root) return;
  const nowIso = new Date().toISOString();

  mountCrud(root, {
    title: 'Trust score policies',
    subtitle: 'Backed by trust_score_policy. Deletions are soft deletes (is_deleted=true).',
    idField: 'id',
    listUrl: '/v1/policies/trust-score-policies?size=200',
    createUrl: '/v1/policies/trust-score-policies',
    updateUrl: (id) => `/v1/policies/trust-score-policies/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/trust-score-policies/${encodeURIComponent(id)}`,
    columns: [
      { name: 'id', label: 'ID' },
      { name: 'policy_code', label: 'Code' },
      { name: 'source_type', label: 'Source' },
      { name: 'signal_key', label: 'Signal' },
      { name: 'score_delta', label: 'Δ' },
      { name: 'status', label: 'Status' }
    ],
    fields: [
      { name: 'policy_code', label: 'Policy code', required: true, placeholder: 'POLICY_001' },
      { name: 'source_type', label: 'Source type', type: 'select', required: true, defaultValue: 'SYSTEM_RULE', options: ['SYSTEM_RULE', 'REJECT_APPLICATION', 'POSTURE_SIGNAL', 'MANUAL'] },
      { name: 'signal_key', label: 'Signal key', required: true, placeholder: 'OS_EOL' },
      { name: 'severity', label: 'Severity (1-5)', type: 'number', placeholder: 'Optional' },
      { name: 'compliance_action', label: 'Compliance action', type: 'select', options: ['', 'ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY'], defaultValue: '' },
      { name: 'score_delta', label: 'Score delta', type: 'number', required: true, defaultValue: -10 },
      { name: 'weight', label: 'Weight', type: 'number', required: true, defaultValue: 1.0 },
      { name: 'status', label: 'Status', type: 'select', required: true, defaultValue: 'ACTIVE', options: ['ACTIVE', 'INACTIVE'] },
      { name: 'effective_from', label: 'Effective from (ISO-8601)', required: true, defaultValue: nowIso, placeholder: '2026-02-27T10:00:00Z' },
      { name: 'effective_to', label: 'Effective to (ISO-8601)', placeholder: 'Optional' }
    ]
  });
});

