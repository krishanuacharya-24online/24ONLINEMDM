import { mountDtCrud } from '../dt-crud.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';

const SUPPORT_STATE_OPTIONS = ['TRACKED', 'SUPPORTED', 'NOT_FOUND'];
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];

document.addEventListener('DOMContentLoaded', async () => {
  await populateLookupSelect('support_state', {
    fallbackOptions: SUPPORT_STATE_OPTIONS
  });
  await populateLookupSelect('status', {
    lookupType: LOOKUP_TYPES.recordStatus,
    fallbackOptions: STATUS_OPTIONS
  });

  const crud = mountDtCrud({
    tableSelector: '#osLifecycleTable',
    ajaxUrl: '/v1/ui/datatables/os-lifecycle',
    idField: 'id',
    createUrl: '/v1/os-lifecycle',
    updateUrl: (id) => `/v1/os-lifecycle/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/os-lifecycle/${encodeURIComponent(id)}`,
    formId: 'osLifecycleForm',
    resetId: 'resetBtn',
    fieldIds: [
      'platform_code', 'os_type', 'os_name', 'cycle',
      'released_on', 'eol_on', 'eeol_on', 'latest_version',
      'support_state', 'source_name', 'source_url', 'notes', 'status'
    ],
    defaults: () => ({ support_state: 'TRACKED', status: 'ACTIVE' }),
    columns: [
      { data: 'id' },
      { data: 'platform_code' },
      { data: 'cycle' },
      { data: 'released_on' },
      { data: 'eol_on' },
      { data: 'support_state' },
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
