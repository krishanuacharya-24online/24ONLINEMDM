import { apiFetch } from '../api.js';

function badgeClassForStatus(status) {
  const normalized = String(status || '').trim().toUpperCase();
  if (!normalized) return 'badge';
  if (normalized === 'TRUSTED') return 'badge badge--trusted';
  if (normalized === 'LOW_RISK') return 'badge badge--low-risk';
  if (normalized === 'MEDIUM_RISK') return 'badge badge--medium-risk';
  if (normalized === 'HIGH_RISK') return 'badge badge--high-risk';
  if (normalized === 'CRITICAL') return 'badge badge--critical';
  if (normalized.includes('FAIL') || normalized.includes('ERROR') || normalized.includes('REJECT')) {
    return 'badge badge--danger';
  }
  if (normalized.includes('PENDING') || normalized.includes('RECEIVED') || normalized.includes('QUEUED')) {
    return 'badge badge--warn';
  }
  return 'badge badge--ok';
}

async function ingest(e) {
  e.preventDefault();
  const tenantId = (document.getElementById('tenantId').value || '').trim();
  const tenantKey = (document.getElementById('tenantKey').value || '').trim();
  const deviceId = (document.getElementById('deviceExternalId').value || '').trim();
  const out = document.getElementById('ingest-result');
  out.textContent = 'Sending...';

  if (!tenantId) {
    out.textContent = 'Error: Tenant ID is required.';
    return;
  }

  try {
    const payload = {
      device_external_id: deviceId,
      agent_id: 'ui-console',
      payload_version: '1',
      payload_json: { sample: true, ts: new Date().toISOString() }
    };
    const headers = { 'X-Tenant-Id': tenantId };
    if (tenantKey) headers['X-Tenant-Key'] = tenantKey;
    const resp = await apiFetch('/v1/agent/posture-payloads', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload)
    });
    out.textContent = JSON.stringify(resp, null, 2);
    document.getElementById('payload-table')?.__dt?.ajax.reload();
  } catch (err) {
    out.textContent = `Error: ${err.message}`;
  }
}

document.addEventListener('DOMContentLoaded', () => {
  const dt = window.mdmInitDataTable('#payload-table', {
    ajax: { url: '/v1/ui/datatables/posture-payloads', dataSrc: 'data' },
    defaultSortBy: 'received_at',
    defaultSortDir: 'desc',
    extraParams: () => ({
      tenant_id: (document.getElementById('tenantId')?.value || '').trim()
    }),
    columns: [
      { data: 'id' },
      { data: 'tenant_id' },
      { data: 'device_external_id' },
      {
        data: 'process_status',
        render: (value, type, row, helpers) => `<span class="${badgeClassForStatus(value)}">${helpers.escapeHtml(value || '')}</span>`
      },
      { data: 'received_at' }
    ]
  });

  const table = document.getElementById('payload-table');
  if (table) table.__dt = dt;

  document.getElementById('ingest-form')?.addEventListener('submit', ingest);
});
