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

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
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

  mountSubscriptionPlanCatalog().catch((e) => window.mdmToast?.(`Subscription plan panel error: ${e.message}`));
  mountTenantKeyPanel().catch((e) => window.mdmToast?.(`Tenant key panel error: ${e.message}`));
  mountTenantSubscriptionPanel().catch((e) => window.mdmToast?.(`Tenant subscription panel error: ${e.message}`));
});

async function mountSubscriptionPlanCatalog() {
  const form = document.getElementById('subscriptionPlanForm');
  if (!form) return;

  const tableBody = document.getElementById('subscriptionPlansTableBody');
  const editIdInput = document.getElementById('subscriptionPlanEditId');
  const codeInput = document.getElementById('subscriptionPlanCodeInput');
  const nameInput = document.getElementById('subscriptionPlanNameInput');
  const descriptionInput = document.getElementById('subscriptionPlanDescriptionInput');
  const statusInput = document.getElementById('subscriptionPlanStatusInput');
  const maxDevicesInput = document.getElementById('subscriptionPlanMaxDevicesInput');
  const maxUsersInput = document.getElementById('subscriptionPlanMaxUsersInput');
  const maxPayloadsInput = document.getElementById('subscriptionPlanMaxPayloadsInput');
  const retentionInput = document.getElementById('subscriptionPlanRetentionInput');
  const premiumInput = document.getElementById('subscriptionPlanPremiumInput');
  const advancedInput = document.getElementById('subscriptionPlanAdvancedInput');
  const saveBtn = document.getElementById('saveSubscriptionPlanBtn');
  const resetBtn = document.getElementById('resetSubscriptionPlanBtn');
  const reloadBtn = document.getElementById('reloadSubscriptionPlanBtn');

  let plans = [];

  await populateLookupSelect('subscriptionPlanStatusInput', {
    lookupType: LOOKUP_TYPES.recordStatus,
    fallbackOptions: STATUS_OPTIONS
  });

  function resetForm() {
    editIdInput.value = '';
    codeInput.value = '';
    nameInput.value = '';
    descriptionInput.value = '';
    statusInput.value = 'ACTIVE';
    maxDevicesInput.value = '25';
    maxUsersInput.value = '10';
    maxPayloadsInput.value = '5000';
    retentionInput.value = '30';
    premiumInput.checked = false;
    advancedInput.checked = false;
    saveBtn.textContent = 'Save plan';
  }

  function fillForm(plan) {
    editIdInput.value = String(getValue(plan, 'id') || '');
    codeInput.value = getValue(plan, 'plan_code', 'planCode') || '';
    nameInput.value = getValue(plan, 'plan_name', 'planName') || '';
    descriptionInput.value = getValue(plan, 'description') || '';
    statusInput.value = getValue(plan, 'status') || 'ACTIVE';
    maxDevicesInput.value = String(getValue(plan, 'max_active_devices', 'maxActiveDevices') || '');
    maxUsersInput.value = String(getValue(plan, 'max_tenant_users', 'maxTenantUsers') || '');
    maxPayloadsInput.value = String(getValue(plan, 'max_monthly_payloads', 'maxMonthlyPayloads') || '');
    retentionInput.value = String(getValue(plan, 'data_retention_days', 'dataRetentionDays') || '');
    premiumInput.checked = Boolean(getValue(plan, 'premium_reporting_enabled', 'premiumReportingEnabled'));
    advancedInput.checked = Boolean(getValue(plan, 'advanced_controls_enabled', 'advancedControlsEnabled'));
    saveBtn.textContent = 'Update plan';
  }

  function renderTable() {
    if (!plans.length) {
      tableBody.innerHTML = '<tr><td colspan="9" class="muted">No subscription plans found.</td></tr>';
      return;
    }

    tableBody.innerHTML = plans.map((plan) => {
      const id = getValue(plan, 'id');
      const code = escapeHtml(getValue(plan, 'plan_code', 'planCode') || '-');
      const name = escapeHtml(getValue(plan, 'plan_name', 'planName') || '-');
      const status = escapeHtml(getValue(plan, 'status') || '-');
      const devices = escapeHtml(getValue(plan, 'max_active_devices', 'maxActiveDevices') ?? '-');
      const users = escapeHtml(getValue(plan, 'max_tenant_users', 'maxTenantUsers') ?? '-');
      const payloads = escapeHtml(getValue(plan, 'max_monthly_payloads', 'maxMonthlyPayloads') ?? '-');
      const retention = escapeHtml(getValue(plan, 'data_retention_days', 'dataRetentionDays') ?? '-');
      const premium = Boolean(getValue(plan, 'premium_reporting_enabled', 'premiumReportingEnabled'));
      const advanced = Boolean(getValue(plan, 'advanced_controls_enabled', 'advancedControlsEnabled'));
      const features = [
        premium ? 'Premium reporting' : null,
        advanced ? 'Advanced controls' : null
      ].filter(Boolean).join(', ') || 'Standard';
      const retireButton = String(status).toUpperCase() === 'ACTIVE'
        ? `<button class="danger" data-plan-act="retire" data-plan-id="${escapeHtml(id)}">Retire</button>`
        : '';
      return `
        <tr>
          <td>${code}</td>
          <td>${name}</td>
          <td>${status}</td>
          <td>${devices}</td>
          <td>${users}</td>
          <td>${payloads}</td>
          <td>${retention}</td>
          <td>${escapeHtml(features)}</td>
          <td>
            <button class="secondary" data-plan-act="edit" data-plan-id="${escapeHtml(id)}">Edit</button>
            ${retireButton}
          </td>
        </tr>`;
    }).join('');
  }

  async function loadPlans(options = {}) {
    const preserveId = options.preserveId == null ? editIdInput.value : String(options.preserveId || '');
    plans = await apiFetch('/v1/admin/tenants/subscription-plans/catalog');
    plans = Array.isArray(plans) ? plans : [];
    renderTable();

    if (preserveId) {
      const current = plans.find((plan) => String(getValue(plan, 'id') || '') === preserveId);
      if (current) {
        fillForm(current);
        return;
      }
    }
    resetForm();
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const planId = editIdInput.value || '';
    const payload = {
      plan_code: codeInput.value || null,
      plan_name: nameInput.value || null,
      description: descriptionInput.value || null,
      max_active_devices: Number(maxDevicesInput.value || 0),
      max_tenant_users: Number(maxUsersInput.value || 0),
      max_monthly_payloads: Number(maxPayloadsInput.value || 0),
      data_retention_days: Number(retentionInput.value || 0),
      premium_reporting_enabled: premiumInput.checked,
      advanced_controls_enabled: advancedInput.checked,
      status: statusInput.value || 'ACTIVE'
    };

    saveBtn.disabled = true;
    try {
      if (planId) {
        await apiFetch(`/v1/admin/tenants/subscription-plans/${encodeURIComponent(planId)}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        window.mdmToast?.('Subscription plan updated');
      } else {
        await apiFetch('/v1/admin/tenants/subscription-plans', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        window.mdmToast?.('Subscription plan created');
      }
      await loadPlans();
      document.dispatchEvent(new CustomEvent('subscription-plans-changed'));
    } catch (error) {
      window.mdmToast?.(`Failed to save plan: ${error.message}`);
    } finally {
      saveBtn.disabled = false;
    }
  });

  tableBody.addEventListener('click', async (event) => {
    const button = event.target.closest('button[data-plan-act]');
    if (!button) return;

    const planId = String(button.getAttribute('data-plan-id') || '');
    const plan = plans.find((entry) => String(getValue(entry, 'id') || '') === planId);
    if (!plan) return;

    const action = button.getAttribute('data-plan-act');
    if (action === 'edit') {
      fillForm(plan);
      codeInput.focus();
      return;
    }

    if (action === 'retire') {
      const planCode = getValue(plan, 'plan_code', 'planCode') || 'this plan';
      const confirmed = typeof window.mdmConfirm === 'function'
        ? await window.mdmConfirm({
          title: 'Retire subscription plan',
          message: `Retire plan ${planCode}? Existing tenants keep their current plan, but it will no longer be assignable.`,
          confirmLabel: 'Retire plan',
          cancelLabel: 'Cancel'
        })
        : window.confirm(`Retire plan ${planCode}?`);
      if (!confirmed) return;

      button.disabled = true;
      try {
        await apiFetch(`/v1/admin/tenants/subscription-plans/${encodeURIComponent(planId)}/retire`, {
          method: 'POST'
        });
        await loadPlans({ preserveId: editIdInput.value || '' });
        document.dispatchEvent(new CustomEvent('subscription-plans-changed'));
        window.mdmToast?.(`Plan ${planCode} retired`);
      } catch (error) {
        window.mdmToast?.(`Failed to retire plan: ${error.message}`);
      } finally {
        button.disabled = false;
      }
    }
  });

  resetBtn?.addEventListener('click', () => resetForm());
  reloadBtn?.addEventListener('click', () => {
    loadPlans({ preserveId: editIdInput.value || '' }).catch((e) => window.mdmToast?.(`Failed to reload plans: ${e.message}`));
  });

  resetForm();
  await loadPlans();
}

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
    const plans = await apiFetch('/v1/admin/tenants/subscription-plans/catalog');
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
      const status = String(getValue(plan, 'status') || 'ACTIVE').toUpperCase();
      const name = getValue(plan, 'plan_name', 'planName') || option.value;
      option.textContent = status === 'ACTIVE'
        ? `${option.value} - ${name}`
        : `${option.value} - ${name} (inactive)`;
      option.disabled = status !== 'ACTIVE';
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

    const currentPlanCode = getValue(subscription, 'plan_code', 'planCode') || '';
    const hasCurrentPlanOption = Array.from(planSelect.options).some((option) => option.value === currentPlanCode);
    if (currentPlanCode && !hasCurrentPlanOption) {
      const fallbackOption = document.createElement('option');
      fallbackOption.value = currentPlanCode;
      fallbackOption.textContent = `${currentPlanCode} - ${getValue(subscription, 'plan_name', 'planName') || currentPlanCode} (inactive)`;
      fallbackOption.disabled = true;
      planSelect.appendChild(fallbackOption);
    }
    planSelect.value = currentPlanCode;
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

  document.addEventListener('subscription-plans-changed', () => {
    loadPlans()
      .then(() => refreshSubscriptionState())
      .catch((e) => window.mdmToast?.(`Failed to refresh plan choices: ${e.message}`));
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
