import { mountCrud } from '../crud.js';

document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('crud-root');
  if (!root) return;
  const nowIso = new Date().toISOString();

  mountCrud(root, {
    title: 'Decision policies',
    subtitle: 'Backed by trust_score_decision_policy. Deletions are soft deletes (is_deleted=true).',
    idField: 'id',
    listUrl: '/v1/policies/trust-decision-policies?size=200',
    createUrl: '/v1/policies/trust-decision-policies',
    updateUrl: (id) => `/v1/policies/trust-decision-policies/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/trust-decision-policies/${encodeURIComponent(id)}`,
    columns: [
      { name: 'id', label: 'ID' },
      { name: 'policy_name', label: 'Name' },
      { name: 'score_min', label: 'Min' },
      { name: 'score_max', label: 'Max' },
      { name: 'decision_action', label: 'Action' },
      { name: 'status', label: 'Status' }
    ],
    fields: [
      { name: 'policy_name', label: 'Policy name', required: true, placeholder: 'Default decision' },
      { name: 'score_min', label: 'Score min', type: 'number', required: true, defaultValue: 0 },
      { name: 'score_max', label: 'Score max', type: 'number', required: true, defaultValue: 100 },
      { name: 'decision_action', label: 'Decision action', type: 'select', required: true, defaultValue: 'ALLOW', options: ['ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY'] },
      { name: 'remediation_required', label: 'Remediation required', type: 'boolean', defaultValue: false },
      { name: 'response_message', label: 'Response message', placeholder: 'Optional' },
      { name: 'status', label: 'Status', type: 'select', required: true, defaultValue: 'ACTIVE', options: ['ACTIVE', 'INACTIVE'] },
      { name: 'effective_from', label: 'Effective from (ISO-8601)', required: true, defaultValue: nowIso, placeholder: '2026-02-27T10:00:00Z' },
      { name: 'effective_to', label: 'Effective to (ISO-8601)', placeholder: 'Optional' }
    ]
  });
});

