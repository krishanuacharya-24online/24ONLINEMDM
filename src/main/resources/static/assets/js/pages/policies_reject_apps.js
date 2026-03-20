import { mountCrud } from '../crud.js';

document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('crud-root');
  if (!root) return;
  const nowIso = new Date().toISOString();

  mountCrud(root, {
    title: 'Reject apps',
    subtitle: 'Backed by reject_application_list. Deletions are soft deletes (is_deleted=true).',
    idField: 'id',
    listUrl: '/v1/policies/reject-apps?size=200',
    createUrl: '/v1/policies/reject-apps',
    updateUrl: (id) => `/v1/policies/reject-apps/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/reject-apps/${encodeURIComponent(id)}`,
    columns: [
      { name: 'id', label: 'ID' },
      { name: 'app_os_type', label: 'OS' },
      { name: 'app_name', label: 'App' },
      { name: 'package_id', label: 'Package' },
      { name: 'severity', label: 'Sev' },
      { name: 'status', label: 'Status' }
    ],
    fields: [
      { name: 'policy_tag', label: 'Policy tag', required: true, placeholder: 'DEFAULT' },
      { name: 'severity', label: 'Severity (1-5)', type: 'number', required: true, defaultValue: 3 },
      { name: 'app_os_type', label: 'App OS type', type: 'select', required: true, defaultValue: 'ANDROID', options: ['ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'] },
      { name: 'app_name', label: 'App name', required: true, placeholder: 'Example VPN' },
      { name: 'publisher', label: 'Publisher', placeholder: 'Optional' },
      { name: 'package_id', label: 'Package ID', placeholder: 'Optional' },
      { name: 'min_allowed_version', label: 'Min allowed version', required: true, placeholder: '1.2.3' },
      { name: 'status', label: 'Status', type: 'select', required: true, defaultValue: 'ACTIVE', options: ['ACTIVE', 'INACTIVE'] },
      { name: 'effective_from', label: 'Effective from (ISO-8601)', required: true, defaultValue: nowIso, placeholder: '2026-02-27T10:00:00Z' },
      { name: 'effective_to', label: 'Effective to (ISO-8601)', placeholder: 'Optional' }
    ]
  });
});
