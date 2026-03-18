import { mountDtCrud } from '../dt-crud.js';
import { apiFetch } from '../api.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';

const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];

function getValue(obj, ...keys) {
  for (const key of keys) {
    if (obj && obj[key] !== undefined && obj[key] !== null) {
      return obj[key];
    }
  }
  return null;
}

function toTenantRows(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.content)) return payload.content;
  if (Array.isArray(payload?.data)) return payload.data;
  return [];
}

document.addEventListener('DOMContentLoaded', async () => {
  await populateLookupSelect('status', {
    lookupType: LOOKUP_TYPES.recordStatus,
    fallbackOptions: STATUS_OPTIONS
  });

  mountDtCrud({
    tableSelector: '#tenantsTable',
    ajaxUrl: '/v1/ui/datatables/tenants',
    idField: 'id',
    createUrl: '/v1/admin/tenants',
    updateUrl: (id) => `/v1/admin/tenants/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/admin/tenants/${encodeURIComponent(id)}`,
    formId: 'tenantsForm',
    resetId: 'resetBtn',
    fieldIds: ['tenant_id', 'name', 'status'],
    defaults: () => ({ status: 'ACTIVE' }),
    columns: [
      { data: 'id' },
      { data: 'tenant_id' },
      { data: 'name' },
      { data: 'status' },
      { data: 'modified_at' },
      {
        data: null,
        orderable: false,
        render: () =>
          `<button class="secondary" data-act="edit">Edit</button>
           <button class="danger" data-act="del">Delete</button>`
      }
    ]
  });

  mountTenantKeyPanel().catch((e) => window.mdmToast?.(`Tenant key panel error: ${e.message}`));
});

async function mountTenantKeyPanel() {
  const form = document.getElementById('tenantKeyForm');
  if (!form) return;

  const tenantSelect = document.getElementById('tenantKeyTenant');
  const meta = document.getElementById('tenantKeyMeta');
  const result = document.getElementById('tenantKeyResult');
  const generatedKey = document.getElementById('tenantGeneratedKey');
  const copyBtn = document.getElementById('copyTenantGeneratedKey');
  const reloadBtn = document.getElementById('reloadTenantKeyTenants');

  async function loadTenantOptions() {
    const activeTenantId = tenantSelect.value;
    tenantSelect.disabled = true;
    tenantSelect.innerHTML = '<option value="">Loading tenants...</option>';

    try {
      const items = await apiFetch('/v1/admin/tenants?size=500');
      const tenants = toTenantRows(items);

      tenantSelect.innerHTML = '';
      const placeholder = document.createElement('option');
      placeholder.value = '';
      placeholder.textContent = tenants.length ? 'Select tenant...' : 'No tenants available';
      tenantSelect.appendChild(placeholder);

      tenants.forEach((tenant) => {
        const id = getValue(tenant, 'id');
        const tenantId = getValue(tenant, 'tenant_id', 'tenantId') || '';
        const name = getValue(tenant, 'name') || '';
        if (id == null) return;

        const option = document.createElement('option');
        option.value = String(id);
        option.textContent = `${tenantId} - ${name}`;
        tenantSelect.appendChild(option);
      });

      if (activeTenantId && tenantSelect.querySelector(`option[value="${activeTenantId}"]`)) {
        tenantSelect.value = activeTenantId;
      } else {
        tenantSelect.value = '';
      }

      await refreshKeyMetadata();
    } catch (error) {
      tenantSelect.innerHTML = '<option value="">Failed to load tenants</option>';
      tenantSelect.value = '';
      meta.textContent = 'No active key.';
      result.hidden = true;
      generatedKey.value = '';
      throw error;
    } finally {
      tenantSelect.disabled = false;
    }
  }

  async function refreshKeyMetadata() {
    const tenantId = tenantSelect.value;
    result.hidden = true;
    generatedKey.value = '';
    if (!tenantId) {
      meta.textContent = 'No active key.';
      return;
    }

    let keyMeta;
    try {
      keyMeta = await apiFetch(`/v1/admin/tenants/${encodeURIComponent(tenantId)}/keys/active`);
    } catch (error) {
      const message = (error && error.message ? error.message : '').toLowerCase();
      if (message.includes('404') || message.includes('tenant not found')) {
        meta.textContent = 'Tenant not found.';
        return;
      }
      throw error;
    }

    const active = Boolean(getValue(keyMeta, 'active'));
    if (!active) {
      meta.textContent = 'No active key.';
      return;
    }

    const hint = getValue(keyMeta, 'key_hint', 'keyHint') || 'hidden';
    const createdAt = getValue(keyMeta, 'created_at', 'createdAt');
    const createdText = createdAt ? ` (created ${new Date(createdAt).toLocaleString()})` : '';
    meta.textContent = `Active key: ${hint}${createdText}`;
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const tenantId = tenantSelect.value;
    if (!tenantId) {
      window.mdmToast?.('Select a tenant first');
      return;
    }

    const rotateButton = document.getElementById('rotateTenantKeyBtn');
    rotateButton.disabled = true;
    try {
      const response = await apiFetch(`/v1/admin/tenants/${encodeURIComponent(tenantId)}/keys/rotate`, {
        method: 'POST'
      });
      const key = getValue(response, 'key');
      const hint = getValue(response, 'key_hint', 'keyHint') || 'hidden';
      if (!key) {
        throw new Error('Failed to generate tenant key');
      }

      generatedKey.value = key;
      result.hidden = false;
      meta.textContent = `Active key: ${hint}`;
      window.mdmToast?.('Tenant key generated. Copy it now.');
    } catch (error) {
      window.mdmToast?.(`Failed to rotate tenant key: ${error.message}`);
    } finally {
      rotateButton.disabled = false;
    }
  });

  tenantSelect.addEventListener('change', () => {
    refreshKeyMetadata().catch((e) => window.mdmToast?.(`Failed to load key metadata: ${e.message}`));
  });

  copyBtn?.addEventListener('click', async () => {
    const key = generatedKey.value || '';
    if (!key) return;
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(key);
        window.mdmToast?.('Key copied');
        return;
      }
      generatedKey.focus();
      generatedKey.select();
      document.execCommand('copy');
      window.mdmToast?.('Key copied');
    } catch (error) {
      window.mdmToast?.(`Copy failed: ${error.message}`);
    }
  });

  reloadBtn?.addEventListener('click', () => {
    loadTenantOptions().catch((e) => window.mdmToast?.(`Failed to reload tenants: ${e.message}`));
  });

  await loadTenantOptions();
}
