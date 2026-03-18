function formatUtcTimestamp(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

document.addEventListener('DOMContentLoaded', () => {
  const categoryField = document.getElementById('audit_event_category');
  const statusField = document.getElementById('audit_status');
  const eventTypeField = document.getElementById('audit_event_type');
  const actionField = document.getElementById('audit_action');
  const actorField = document.getElementById('audit_actor');

  const table = window.mdmInitDataTable('#auditEventTable', {
    ajax: { url: '/v1/ui/datatables/audit-events', dataSrc: 'data' },
    extraParams: () => ({
      event_category: categoryField?.value || null,
      status: statusField?.value || null,
      event_type: eventTypeField?.value?.trim() || null,
      action: actionField?.value?.trim() || null,
      actor: actorField?.value?.trim() || null
    }),
    columns: [
      { data: 'id' },
      {
        data: 'created_at',
        render: (value) => formatUtcTimestamp(value)
      },
      { data: 'event_category' },
      { data: 'event_type' },
      {
        data: 'action',
        render: (value) => value || '<span class="muted">n/a</span>'
      },
      { data: 'status' },
      {
        data: 'tenant_id',
        render: (value) => value || '<span class="muted">GLOBAL</span>'
      },
      { data: 'actor' },
      {
        data: 'entity_type',
        render: (value) => value || '<span class="muted">n/a</span>'
      },
      {
        data: 'entity_id',
        render: (value) => value || '<span class="muted">n/a</span>'
      }
    ],
    defaultSortBy: 'created_at',
    defaultSortDir: 'desc'
  });

  [categoryField, statusField].forEach((field) => {
    field?.addEventListener('change', () => table.ajax.reload(null, true));
  });

  let debounceHandle = null;
  [eventTypeField, actionField, actorField].forEach((field) => {
    field?.addEventListener('input', () => {
      window.clearTimeout(debounceHandle);
      debounceHandle = window.setTimeout(() => table.ajax.reload(null, true), 250);
    });
  });
});

