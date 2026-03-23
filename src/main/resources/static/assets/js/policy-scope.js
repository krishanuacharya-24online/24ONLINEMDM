import { apiFetch, fetchAuthenticatedUser } from './api.js';

const ROLE_PRODUCT_ADMIN = 'PRODUCT_ADMIN';
const ROLE_TENANT_ADMIN = 'TENANT_ADMIN';
const ROLE_AUDITOR = 'AUDITOR';
const STORAGE_KEY = 'mdm.policy.scope.v1';

function normalizeRole(role) {
  return String(role || '').trim().toUpperCase();
}

function normalizeOptionalTenantId(value) {
  const tenantId = String(value || '').trim();
  return tenantId ? tenantId : null;
}

function readPersistedScope() {
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') return null;
    return {
      mode: parsed.mode === 'tenant' ? 'tenant' : 'global',
      tenantId: normalizeOptionalTenantId(parsed.tenantId)
    };
  } catch {
    return null;
  }
}

function persistScope(mode, tenantId) {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
      mode: mode === 'tenant' ? 'tenant' : 'global',
      tenantId: normalizeOptionalTenantId(tenantId)
    }));
  } catch {
    // Ignore persistence issues (e.g. storage blocked) and continue.
  }
}

function tenantLabel(tenant) {
  if (!tenant || !tenant.tenantId) return '';
  if (tenant.name) return `${tenant.tenantId} - ${tenant.name}`;
  return tenant.tenantId;
}

async function fetchTenants() {
  const rows = await apiFetch('/v1/admin/tenants?size=500');
  if (!Array.isArray(rows)) return [];

  return rows
    .map((tenant) => {
      const tenantId = normalizeOptionalTenantId(tenant?.tenant_id ?? tenant?.tenantId);
      if (!tenantId) return null;
      return {
        tenantId,
        name: String(tenant?.name || '').trim()
      };
    })
    .filter(Boolean);
}

function createElement(tag, attrs = {}) {
  const node = document.createElement(tag);
  Object.entries(attrs).forEach(([key, value]) => {
    if (key === 'text') {
      node.textContent = String(value ?? '');
      return;
    }
    if (key === 'class') {
      node.className = String(value || '');
      return;
    }
    if (value != null) {
      node.setAttribute(key, String(value));
    }
  });
  return node;
}

function resolveHeadersFromTenantId(tenantId) {
  if (!tenantId) return {};
  return { 'X-Tenant-Id': tenantId };
}

function resolveScopeHint(state) {
  if (state.role === ROLE_TENANT_ADMIN) {
    return 'Policy scope: your tenant. Shared global references remain available where this page supports them.';
  }
  if (state.role === ROLE_AUDITOR) {
    return 'Policy scope: your tenant audit view.';
  }
  if (state.mode !== 'tenant') {
    return 'Policy scope: global';
  }
  if (!state.tenantId) {
    return 'Policy scope: choose a tenant';
  }
  return `Policy scope: tenant ${state.tenantId}. Shared global references remain available where this page supports them.`;
}

function applyProductAdminScope(state, mode, tenantId, tenantOptions) {
  const requestedMode = mode === 'tenant' ? 'tenant' : 'global';
  if (requestedMode === 'global') {
    state.mode = 'global';
    state.tenantId = null;
    persistScope(state.mode, state.tenantId);
    return;
  }

  const requestedTenantId = normalizeOptionalTenantId(tenantId);
  const validTenant = tenantOptions.find((entry) => entry.tenantId === requestedTenantId);
  if (validTenant) {
    state.mode = 'tenant';
    state.tenantId = validTenant.tenantId;
    persistScope(state.mode, state.tenantId);
    return;
  }

  if (tenantOptions.length) {
    state.mode = 'tenant';
    state.tenantId = tenantOptions[0].tenantId;
    persistScope(state.mode, state.tenantId);
    return;
  }

  state.mode = 'global';
  state.tenantId = null;
  persistScope(state.mode, state.tenantId);
}

function renderScopeControl(state, tenantOptions, onChange, mountSelector) {
  const mount = document.querySelector(mountSelector);
  if (!mount) return;

  const existing = mount.querySelector('[data-policy-scope-control="true"]');
  if (existing) existing.remove();

  const wrap = createElement('div', { class: 'form-row mt-075', 'data-policy-scope-control': 'true' });

  if (state.role === ROLE_TENANT_ADMIN || state.role === ROLE_AUDITOR) {
    const label = createElement('div', { class: 'muted', text: resolveScopeHint(state) });
    wrap.appendChild(label);
    mount.appendChild(wrap);
    return;
  }

  if (state.role !== ROLE_PRODUCT_ADMIN) {
    const label = createElement('div', { class: 'muted', text: 'Policy scope: global' });
    wrap.appendChild(label);
    mount.appendChild(wrap);
    return;
  }

  const modeLabel = createElement('label', { for: 'policy_scope_mode', text: 'Scope' });
  const modeSelect = createElement('select', { id: 'policy_scope_mode' });
  modeSelect.appendChild(createElement('option', { value: 'global', text: 'Global' }));
  modeSelect.appendChild(createElement('option', { value: 'tenant', text: 'Tenant' }));
  modeSelect.value = state.mode;

  const tenantLabelNode = createElement('label', { for: 'policy_scope_tenant', text: 'Tenant' });
  const tenantSelect = createElement('select', { id: 'policy_scope_tenant' });
  tenantSelect.appendChild(createElement('option', { value: '', text: 'Select tenant' }));
  tenantOptions.forEach((tenant) => {
    tenantSelect.appendChild(createElement('option', {
      value: tenant.tenantId,
      text: tenantLabel(tenant)
    }));
  });
  tenantSelect.value = state.tenantId || '';

  const hintNode = createElement('span', { class: 'muted', text: resolveScopeHint(state) });

  function syncControlState() {
    modeSelect.value = state.mode;
    tenantSelect.value = state.tenantId || '';
    tenantSelect.disabled = state.mode !== 'tenant';
    tenantLabelNode.style.display = state.mode === 'tenant' ? '' : 'none';
    tenantSelect.style.display = state.mode === 'tenant' ? '' : 'none';
    hintNode.textContent = resolveScopeHint(state);
  }

  modeSelect.addEventListener('change', () => {
    applyProductAdminScope(state, modeSelect.value, tenantSelect.value, tenantOptions);
    syncControlState();
    onChange();
  });

  tenantSelect.addEventListener('change', () => {
    applyProductAdminScope(state, 'tenant', tenantSelect.value, tenantOptions);
    syncControlState();
    onChange();
  });

  wrap.appendChild(modeLabel);
  wrap.appendChild(modeSelect);
  wrap.appendChild(tenantLabelNode);
  wrap.appendChild(tenantSelect);
  wrap.appendChild(hintNode);
  mount.appendChild(wrap);
  syncControlState();
}

export async function initPolicyScope({
  mountSelector = '.page-head > div:first-child'
} = {}) {
  const me = await fetchAuthenticatedUser().catch(() => null);
  const role = normalizeRole(me?.role);

  const state = {
    role,
    mode: 'global',
    tenantId: null
  };

  let tenants = [];
  if (role === ROLE_PRODUCT_ADMIN) {
    tenants = await fetchTenants().catch(() => []);
    const persisted = readPersistedScope();
    if (persisted && persisted.mode === 'tenant') {
      applyProductAdminScope(state, 'tenant', persisted.tenantId, tenants);
    } else {
      applyProductAdminScope(state, 'global', null, tenants);
    }
  } else if (role === ROLE_TENANT_ADMIN || role === ROLE_AUDITOR) {
    state.mode = 'tenant';
    state.tenantId = null;
  }

  const listeners = new Set();
  const notify = () => {
    listeners.forEach((listener) => {
      try {
        listener({
          mode: state.mode,
          tenantId: state.tenantId,
          role: state.role
        });
      } catch {
        // Keep notifying remaining listeners.
      }
    });
  };

  renderScopeControl(state, tenants, notify, mountSelector);

  return {
    role: state.role,
    mode: () => state.mode,
    tenantId: () => state.tenantId,
    getHeaders: () => {
      if (state.role !== ROLE_PRODUCT_ADMIN) {
        return {};
      }
      return resolveHeadersFromTenantId(state.mode === 'tenant' ? state.tenantId : null);
    },
    onChange: (listener) => {
      if (typeof listener !== 'function') {
        return () => {};
      }
      listeners.add(listener);
      return () => listeners.delete(listener);
    }
  };
}
