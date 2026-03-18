import { mountCrud } from '../crud.js';

document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('crud-root');
  if (!root) return;
  mountCrud(root, {
    title: 'OS lifecycle',
    subtitle: 'Backed by os_release_lifecycle_master. Deletions are soft deletes (is_deleted=true).',
    idField: 'id',
    listUrl: '/v1/os-lifecycle?size=200',
    createUrl: '/v1/os-lifecycle',
    updateUrl: (id) => `/v1/os-lifecycle/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/os-lifecycle/${encodeURIComponent(id)}`,
    columns: [
      { name: 'id', label: 'ID' },
      { name: 'platform_code', label: 'Platform' },
      { name: 'cycle', label: 'Cycle' },
      { name: 'released_on', label: 'Released' },
      { name: 'eol_on', label: 'EOL' },
      { name: 'support_state', label: 'Support' }
    ],
    fields: [
      { name: 'platform_code', label: 'Platform code', required: true, placeholder: 'WINDOWS' },
      { name: 'os_type', label: 'OS type', placeholder: 'WINDOWS' },
      { name: 'os_name', label: 'OS name', placeholder: 'Optional' },
      { name: 'cycle', label: 'Cycle', required: true, placeholder: '11-25h2-e' },
      { name: 'released_on', label: 'Released on (YYYY-MM-DD)', placeholder: '2026-02-10' },
      { name: 'eol_on', label: 'EOL on (YYYY-MM-DD)', placeholder: '2029-03-13' },
      { name: 'eeol_on', label: 'Extended EOL (YYYY-MM-DD)', placeholder: 'Optional' },
      { name: 'latest_version', label: 'Latest version', placeholder: 'Optional' },
      { name: 'support_state', label: 'Support state', type: 'select', required: true, defaultValue: 'TRACKED', options: ['TRACKED', 'SUPPORTED', 'NOT_FOUND'] },
      { name: 'source_name', label: 'Source name', placeholder: 'endoflife.date' },
      { name: 'source_url', label: 'Source URL', placeholder: 'Optional' },
      { name: 'notes', label: 'Notes', type: 'textarea', placeholder: 'Optional' },
      { name: 'status', label: 'Status', type: 'select', required: true, defaultValue: 'ACTIVE', options: ['ACTIVE', 'INACTIVE'] }
    ]
  });
});

