import { mountCrud } from '../crud.js';

document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('crud-root');
  if (!root) return;
  const nowIso = new Date().toISOString();

  mountCrud(root, {
    title: 'Remediation rules',
    subtitle: 'Backed by remediation_rule. Deletions are soft deletes (is_deleted=true).',
    idField: 'id',
    listUrl: '/v1/policies/remediation-rules?size=200',
    createUrl: '/v1/policies/remediation-rules',
    updateUrl: (id) => `/v1/policies/remediation-rules/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/remediation-rules/${encodeURIComponent(id)}`,
    columns: [
      { name: 'id', label: 'ID' },
      { name: 'remediation_code', label: 'Code' },
      { name: 'remediation_type', label: 'Type' },
      { name: 'os_type', label: 'OS' },
      { name: 'device_type', label: 'Device' },
      { name: 'status', label: 'Status' }
    ],
    fields: [
      { name: 'remediation_code', label: 'Remediation code', required: true, placeholder: 'REM_001' },
      { name: 'title', label: 'Title', required: true, placeholder: 'Update OS' },
      { name: 'description', label: 'Description', type: 'textarea', required: true, placeholder: 'Explain the remediation steps' },
      { name: 'remediation_type', label: 'Type', type: 'select', required: true, defaultValue: 'USER_ACTION', options: ['USER_ACTION', 'AUTO_ACTION', 'NETWORK_RESTRICT', 'APP_REMOVAL', 'OS_UPDATE', 'POLICY_ACK'] },
      { name: 'os_type', label: 'OS type', type: 'select', options: ['', 'ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX'], defaultValue: '' },
      { name: 'device_type', label: 'Device type', type: 'select', options: ['', 'PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER'], defaultValue: '' },
      { name: 'priority', label: 'Priority', type: 'number', required: true, defaultValue: 100 },
      { name: 'status', label: 'Status', type: 'select', required: true, defaultValue: 'ACTIVE', options: ['ACTIVE', 'INACTIVE'] },
      { name: 'effective_from', label: 'Effective from (ISO-8601)', required: true, defaultValue: nowIso, placeholder: '2026-02-27T10:00:00Z' },
      { name: 'effective_to', label: 'Effective to (ISO-8601)', placeholder: 'Optional' }
    ]
  });
});

