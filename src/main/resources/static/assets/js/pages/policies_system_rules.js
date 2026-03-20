import { mountCrud } from '../crud.js';

document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('open-conditions-form')?.addEventListener('submit', (e) => {
    e.preventDefault();
    const id = document.getElementById('ruleId')?.value;
    if (!id) return;
    window.location.href = `/ui/policies/system-rules/${encodeURIComponent(id)}/conditions`;
  });

  const root = document.getElementById('crud-root');
  if (!root) return;

  const nowIso = new Date().toISOString();

  mountCrud(root, {
    title: 'System rules',
    subtitle: 'Backed by table system_information_rule. Deletions are soft deletes (is_deleted=true).',
    idField: 'id',
    listUrl: '/v1/policies/system-rules?size=200',
    createUrl: '/v1/policies/system-rules',
    updateUrl: (id) => `/v1/policies/system-rules/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/system-rules/${encodeURIComponent(id)}`,
    columns: [
      { name: 'id', label: 'ID' },
      { name: 'rule_code', label: 'Code' },
      { name: 'os_type', label: 'OS' },
      { name: 'device_type', label: 'Device' },
      { name: 'status', label: 'Status' },
      { name: 'priority', label: 'Priority' }
    ],
    fields: [
      { name: 'rule_code', label: 'Rule code', required: true, placeholder: 'RULE_001' },
      { name: 'rule_tag', label: 'Rule tag', required: true, placeholder: 'BASELINE' },
      { name: 'status', label: 'Status', type: 'select', required: true, defaultValue: 'ACTIVE', options: ['ACTIVE', 'INACTIVE'] },
      { name: 'severity', label: 'Severity (1-5)', type: 'number', required: true, defaultValue: 3 },
      { name: 'priority', label: 'Priority', type: 'number', required: true, defaultValue: 100 },
      { name: 'version', label: 'Version', type: 'number', required: true, defaultValue: 1 },
      { name: 'match_mode', label: 'Match mode', type: 'select', required: true, defaultValue: 'ALL', options: ['ALL', 'ANY'] },
      { name: 'compliance_action', label: 'Compliance action', type: 'select', required: true, defaultValue: 'ALLOW', options: ['ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY'] },
      { name: 'risk_score_delta', label: 'Risk score delta', type: 'number', required: true, defaultValue: 0 },
      { name: 'description', label: 'Description', type: 'textarea', placeholder: 'Optional' },
      { name: 'device_type', label: 'Device type', type: 'select', options: ['', 'PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER'], defaultValue: '' },
      { name: 'os_type', label: 'OS type', type: 'select', required: true, defaultValue: 'ANDROID', options: ['ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'] },
      { name: 'os_name', label: 'OS name (LINUX only)', placeholder: 'UBUNTU' },
      { name: 'effective_from', label: 'Effective from (ISO-8601)', required: true, defaultValue: nowIso, placeholder: '2026-02-27T10:00:00Z' },
      { name: 'effective_to', label: 'Effective to (ISO-8601)', placeholder: 'Optional' }
    ]
  });
});
