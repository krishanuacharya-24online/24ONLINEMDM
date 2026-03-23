import { apiFetch } from './api.js';

const ROLE_PRODUCT_ADMIN = 'PRODUCT_ADMIN';
const ROLE_TENANT_ADMIN = 'TENANT_ADMIN';
const CLONE_MANAGED_KEYS = new Set([
  'id',
  'tenant_id',
  'tenantId',
  'is_deleted',
  'deleted',
  'created_at',
  'createdAt',
  'created_by',
  'createdBy',
  'modified_at',
  'modifiedAt',
  'modified_by',
  'modifiedBy'
]);

function normalizeTenantId(value) {
  const tenantId = String(value || '').trim();
  return tenantId ? tenantId : null;
}

function normalizeRole(role) {
  return String(role || '').trim().toUpperCase();
}

export function rowTenantId(row) {
  return normalizeTenantId(row?.tenant_id ?? row?.tenantId);
}

export function isGlobalPolicyRow(row) {
  return rowTenantId(row) == null;
}

export function isTenantAdminPolicyScope(scope) {
  return normalizeRole(scope?.role) === ROLE_TENANT_ADMIN;
}

function scopeMode(scope) {
  if (typeof scope?.mode === 'function') {
    return String(scope.mode() || '').trim().toLowerCase();
  }
  return String(scope?.mode || '').trim().toLowerCase();
}

function scopeTenantId(scope) {
  if (typeof scope?.tenantId === 'function') {
    return normalizeTenantId(scope.tenantId());
  }
  return normalizeTenantId(scope?.tenantId);
}

export function canWritePolicyRow(scope, row) {
  const role = normalizeRole(scope?.role);
  const rowTenant = rowTenantId(row);

  if (role === ROLE_PRODUCT_ADMIN) {
    const currentScopeTenantId = scopeMode(scope) === 'tenant' ? scopeTenantId(scope) : null;
    if (currentScopeTenantId == null) {
      return rowTenant == null;
    }
    return rowTenant === currentScopeTenantId;
  }

  if (role === ROLE_TENANT_ADMIN) {
    return rowTenant != null;
  }

  return false;
}

export function shouldShowCloneToTenant(scope, row) {
  const role = normalizeRole(scope?.role);
  if (role === ROLE_PRODUCT_ADMIN) {
    return scopeMode(scope) === 'tenant' && scopeTenantId(scope) != null && isGlobalPolicyRow(row);
  }
  return isTenantAdminPolicyScope(scope) && isGlobalPolicyRow(row);
}

export function renderPolicyActionButtons(scope, row, { showClone = true } = {}) {
  if (canWritePolicyRow(scope, row)) {
    return `
      <button class="secondary" data-act="edit">Edit</button>
      <button class="danger" data-act="del">Delete</button>
    `;
  }
  if (showClone && shouldShowCloneToTenant(scope, row)) {
    return '<button class="secondary" data-act="clone" data-icon-action="copy" data-icon-label="Clone to tenant">Clone to tenant</button>';
  }
  return `<span class="muted">Read only</span>`;
}

export function stripCloneManagedFields(payload) {
  if (!payload || typeof payload !== 'object') {
    return {};
  }
  const clone = { ...payload };
  CLONE_MANAGED_KEYS.forEach((key) => {
    delete clone[key];
  });
  return clone;
}

async function confirmClone(message, title = 'Confirm clone') {
  if (typeof window.mdmConfirm === 'function') {
    return window.mdmConfirm({
      title,
      message,
      confirmLabel: 'Clone',
      cancelLabel: 'Cancel',
      danger: false
    });
  }
  return window.confirm(message);
}

export async function clonePolicyRecord({
  id,
  getUrl,
  createUrl,
  requestHeaders = () => ({}),
  entityLabel = 'policy',
  confirmMessage = null,
  transformPayload = null
}) {
  const numericId = Number.parseInt(String(id || ''), 10);
  if (!Number.isFinite(numericId)) {
    throw new Error('Invalid policy id for cloning');
  }

  const prompt = confirmMessage || `Clone ${entityLabel} #${numericId} to your tenant scope?`;
  const confirmed = await confirmClone(prompt);
  if (!confirmed) {
    return null;
  }

  const headers = requestHeaders();
  const source = await apiFetch(getUrl(numericId), { headers });
  let payload = stripCloneManagedFields(source);
  if (typeof transformPayload === 'function') {
    const transformed = transformPayload(payload, source);
    payload = transformed && typeof transformed === 'object' ? transformed : payload;
  }

  return apiFetch(createUrl, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  });
}
