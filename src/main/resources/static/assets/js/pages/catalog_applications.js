import { mountCrud } from '../crud.js';

document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('crud-root');
  if (!root) return;

  mountCrud(root, {
    title: 'Applications',
    subtitle: 'Backed by application_catalog.',
    idField: 'id',
    listUrl: '/v1/catalog/applications?size=200',
    createUrl: '/v1/catalog/applications',
    updateUrl: (id) => `/v1/catalog/applications/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/catalog/applications/${encodeURIComponent(id)}`,
    columns: [
      { name: 'id', label: 'ID' },
      { name: 'os_type', label: 'OS' },
      { name: 'package_id', label: 'Package' },
      { name: 'app_name', label: 'Name' },
      { name: 'publisher', label: 'Publisher' },
      { name: 'modified_at', label: 'Modified' }
    ],
    fields: [
      { name: 'os_type', label: 'OS type', type: 'select', required: true, defaultValue: 'ANDROID', options: ['ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX'] },
      { name: 'package_id', label: 'Package ID', placeholder: 'Optional' },
      { name: 'app_name', label: 'App name', required: true, placeholder: 'Example App' },
      { name: 'publisher', label: 'Publisher', placeholder: 'Optional' }
    ]
  });
});

