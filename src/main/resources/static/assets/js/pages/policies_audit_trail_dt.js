import { initPolicyScope } from '../policy-scope.js';

function formatUtcTimestamp(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

document.addEventListener('DOMContentLoaded', async () => {
  const scope = await initPolicyScope();
  const policyTypeField = document.getElementById('audit_policy_type');
  const operationField = document.getElementById('audit_operation');
  const actorField = document.getElementById('audit_actor');

  const table = window.mdmInitDataTable('#policyAuditTable', {
    requestHeaders: () => scope.getHeaders(),
    ajax: { url: '/v1/ui/datatables/policy-audit', dataSrc: 'data' },
    extraParams: () => ({
      policy_type: policyTypeField?.value || null,
      operation: operationField?.value || null,
      actor: actorField?.value?.trim() || null
    }),
    columns: [
      { data: 'id' },
      {
        data: 'created_at',
        render: (value) => formatUtcTimestamp(value)
      },
      { data: 'policy_type' },
      { data: 'policy_id' },
      { data: 'operation' },
      {
        data: 'tenant_id',
        render: (value) => value || '<span class="muted">GLOBAL</span>'
      },
      { data: 'actor' },
      {
        data: 'approval_ticket',
        render: (value) => value || '<span class="muted">n/a</span>'
      }
    ],
    defaultSortBy: 'created_at',
    defaultSortDir: 'desc'
  });

  [policyTypeField, operationField].forEach((field) => {
    field?.addEventListener('change', () => table.ajax.reload(null, true));
  });

  let actorFilterDebounce = null;
  actorField?.addEventListener('input', () => {
    window.clearTimeout(actorFilterDebounce);
    actorFilterDebounce = window.setTimeout(() => table.ajax.reload(null, true), 250);
  });

  scope.onChange(() => {
    table.ajax.reload(null, true);
  });
});
