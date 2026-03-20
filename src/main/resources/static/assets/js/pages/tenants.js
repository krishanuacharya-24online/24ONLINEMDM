import { mountDtCrud } from '../dt-crud.js';
import { apiFetch, apiFetchAllPages } from '../api.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';

const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];
const SUBSCRIPTION_STATES = ['TRIALING', 'ACTIVE', 'GRACE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED', 'EXPIRED'];

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
  mountTenantSubscriptionPanel().catch((e) => window.mdmToast?.(`Tenant subscription panel error: ${e.message}`));
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
      const tenants = await apiFetchAllPages('/v1/admin/tenants', {
        pageSize: 100,
        normalizeRows: toTenantRows
      });

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

async function mountTenantSubscriptionPanel() {
  const form = document.getElementById('tenantSubscriptionForm');
  if (!form) return;

  const tenantSelect = document.getElementById('tenantSubscriptionTenant');
  const reloadBtn = document.getElementById('reloadTenantSubscriptionTenants');
  const refreshBtn = document.getElementById('refreshTenantSubscriptionBtn');
  const planSelect = document.getElementById('subscriptionPlanCode');
  const stateSelect = document.getElementById('subscriptionState');
  const periodEndInput = document.getElementById('subscriptionPeriodEnd');
  const graceEndInput = document.getElementById('subscriptionGraceEnd');
  const notesInput = document.getElementById('subscriptionNotes');
  const featuresEl = document.getElementById('tenantSubscriptionFeatures');
  const usageEl = document.getElementById('tenantUsageSummary');
  const saveBtn = document.getElementById('saveTenantSubscriptionBtn');

  let loadedPlans = [];

  await populateLookupSelect('subscriptionState', {
    lookupType: 'lkp_subscription_state',
    fallbackOptions: SUBSCRIPTION_STATES
  });

  async function loadPlans() {
    const plans = await apiFetch('/v1/admin/tenants/subscription-plans');
    loadedPlans = Array.isArray(plans) ? plans : [];
    planSelect.innerHTML = '';
    if (!loadedPlans.length) {
      const option = document.createElement('option');
      option.value = '';
      option.textContent = 'No plans available';
      planSelect.appendChild(option);
      return;
    }

    loadedPlans.forEach((plan) => {
      const option = document.createElement('option');
      option.value = getValue(plan, 'plan_code', 'planCode') || '';
      option.textContent = `${option.value} - ${getValue(plan, 'plan_name', 'planName') || option.value}`;
      planSelect.appendChild(option);
    });
  }

  async function loadTenantOptions() {
    const selectedTenant = tenantSelect.value;
    tenantSelect.disabled = true;
    tenantSelect.innerHTML = '<option value="">Loading tenants...</option>';

    try {
      const tenants = await apiFetchAllPages('/v1/admin/tenants', {
        pageSize: 100,
        normalizeRows: toTenantRows
      });
      tenantSelect.innerHTML = '';
      const placeholder = document.createElement('option');
      placeholder.value = '';
      placeholder.textContent = tenants.length ? 'Select tenant...' : 'No tenants available';
      tenantSelect.appendChild(placeholder);

      tenants.forEach((tenant) => {
        const option = document.createElement('option');
        option.value = String(getValue(tenant, 'id') || '');
        option.textContent = `${getValue(tenant, 'tenant_id', 'tenantId') || ''} - ${getValue(tenant, 'name') || ''}`;
        tenantSelect.appendChild(option);
      });

      if (selectedTenant && tenantSelect.querySelector(`option[value="${selectedTenant}"]`)) {
        tenantSelect.value = selectedTenant;
      } else {
        tenantSelect.value = '';
      }
      await refreshSubscriptionState();
    } finally {
      tenantSelect.disabled = false;
    }
  }

  async function refreshSubscriptionState() {
    const tenantId = tenantSelect.value;
    if (!tenantId) {
      clearSubscriptionPanel();
      return;
    }

    const [subscription, usage] = await Promise.all([
      apiFetch(`/v1/admin/tenants/${encodeURIComponent(tenantId)}/subscription`),
      apiFetch(`/v1/admin/tenants/${encodeURIComponent(tenantId)}/usage`)
    ]);

    planSelect.value = getValue(subscription, 'plan_code', 'planCode') || '';
    stateSelect.value = getValue(subscription, 'subscription_state', 'subscriptionState') || 'ACTIVE';
    periodEndInput.value = toLocalDateTimeInput(getValue(subscription, 'current_period_end', 'currentPeriodEnd'));
    graceEndInput.value = toLocalDateTimeInput(getValue(subscription, 'grace_ends_at', 'graceEndsAt'));
    notesInput.value = getValue(subscription, 'notes') || '';

    const premiumReporting = Boolean(getValue(subscription, 'premium_reporting_enabled', 'premiumReportingEnabled'));
    const advancedControls = Boolean(getValue(subscription, 'advanced_controls_enabled', 'advancedControlsEnabled'));
    featuresEl.textContent = `Premium reporting: ${premiumReporting ? 'enabled' : 'disabled'} | Advanced controls: ${advancedControls ? 'enabled' : 'disabled'}`;

    const deviceCount = Number(getValue(usage, 'active_device_count', 'activeDeviceCount') || 0);
    const userCount = Number(getValue(usage, 'active_user_count', 'activeUserCount') || 0);
    const payloadCount = Number(getValue(usage, 'posture_payload_count', 'posturePayloadCount') || 0);
    const deviceLimit = getValue(usage, 'max_active_devices', 'maxActiveDevices');
    const userLimit = getValue(usage, 'max_tenant_users', 'maxTenantUsers');
    const payloadLimit = getValue(usage, 'max_monthly_payloads', 'maxMonthlyPayloads');
    usageEl.textContent =
      `Devices ${deviceCount}/${deviceLimit ?? '-'} | Users ${userCount}/${userLimit ?? '-'} | Payloads ${payloadCount}/${payloadLimit ?? '-'}`;
  }

  function clearSubscriptionPanel() {
    if (planSelect.options.length) {
      planSelect.selectedIndex = 0;
    }
    stateSelect.value = 'TRIALING';
    periodEndInput.value = '';
    graceEndInput.value = '';
    notesInput.value = '';
    featuresEl.textContent = 'No tenant selected.';
    usageEl.textContent = 'No tenant selected.';
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const tenantId = tenantSelect.value;
    if (!tenantId) {
      window.mdmToast?.('Select a tenant first');
      return;
    }

    saveBtn.disabled = true;
    try {
      await apiFetch(`/v1/admin/tenants/${encodeURIComponent(tenantId)}/subscription`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          plan_code: planSelect.value,
          subscription_state: stateSelect.value,
          current_period_end: fromLocalDateTimeInput(periodEndInput.value),
          grace_ends_at: fromLocalDateTimeInput(graceEndInput.value),
          notes: notesInput.value || null
        })
      });
      await refreshSubscriptionState();
      window.mdmToast?.('Tenant subscription updated');
    } catch (error) {
      window.mdmToast?.(`Failed to update subscription: ${error.message}`);
    } finally {
      saveBtn.disabled = false;
    }
  });

  tenantSelect.addEventListener('change', () => {
    refreshSubscriptionState().catch((e) => window.mdmToast?.(`Failed to load subscription: ${e.message}`));
  });

  reloadBtn?.addEventListener('click', () => {
    loadTenantOptions().catch((e) => window.mdmToast?.(`Failed to reload tenants: ${e.message}`));
  });

  refreshBtn?.addEventListener('click', () => {
    refreshSubscriptionState().catch((e) => window.mdmToast?.(`Failed to refresh subscription: ${e.message}`));
  });

  await loadPlans();
  await loadTenantOptions();
}

function toLocalDateTimeInput(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function fromLocalDateTimeInput(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
