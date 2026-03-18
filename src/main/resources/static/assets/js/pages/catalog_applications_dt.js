import { mountDtCrud } from '../dt-crud.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';

const APP_OS_OPTIONS = ['ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'];

document.addEventListener('DOMContentLoaded', async () => {
  await populateLookupSelect('os_type', {
    lookupType: LOOKUP_TYPES.osType,
    fallbackOptions: APP_OS_OPTIONS
  });

  const crud = mountDtCrud({
    tableSelector: '#catalogAppsTable',
    ajaxUrl: '/v1/ui/datatables/catalog/applications',
    idField: 'id',
    createUrl: '/v1/catalog/applications',
    updateUrl: (id) => `/v1/catalog/applications/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/catalog/applications/${encodeURIComponent(id)}`,
    formId: 'catalogAppsForm',
    resetId: 'resetBtn',
    fieldIds: ['os_type', 'package_id', 'app_name', 'publisher'],
    defaults: () => ({ os_type: 'ANDROID' }),
    columns: [
      { data: 'id' },
      { data: 'os_type' },
      { data: 'package_id' },
      { data: 'app_name' },
      { data: 'publisher' },
      {
        data: null,
        orderable: false,
        render: () =>
          `<button class="secondary" data-act="edit">Edit</button>
           <button class="danger" data-act="del">Delete</button>`
      }
    ]
  });

  document.addEventListener('reference-sync:completed', () => {
    crud.reload();
  });
});
