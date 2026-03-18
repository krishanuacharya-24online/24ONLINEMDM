import { mountDtCrud } from '../dt-crud.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';
import { initPolicyScope } from '../policy-scope.js';
import {
  canWritePolicyRow,
  clonePolicyRecord,
  renderPolicyActionButtons,
  shouldShowCloneToTenant
} from '../policy-row-actions.js';

const REMEDIATION_TYPE_OPTIONS = ['USER_ACTION', 'AUTO_ACTION', 'NETWORK_RESTRICT', 'APP_REMOVAL', 'OS_UPDATE', 'POLICY_ACK'];
const OS_TYPE_OPTIONS = ['ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX'];
const DEVICE_TYPE_OPTIONS = ['PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER'];
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];

function toInt(value, fallback = 100) {
  const parsed = Number.parseInt(String(value ?? ''), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function setHint(id, text) {
  const node = document.getElementById(id);
  if (node) node.textContent = text;
}

function sanitizeCodeToken(value, fallback, maxLen = 12) {
  const normalized = String(value || '')
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
  if (!normalized) return fallback;
  if (normalized.length > maxLen) return normalized.slice(0, maxLen);
  return normalized;
}

function resolveScopeToken(scope) {
  if (!scope) return 'GLOBAL';
  const role = String(scope.role || '').toUpperCase();
  if (role === 'PRODUCT_ADMIN') {
    if (typeof scope.mode === 'function' && scope.mode() === 'tenant') {
      return sanitizeCodeToken(typeof scope.tenantId === 'function' ? scope.tenantId() : null, 'TENANT', 12);
    }
    return 'GLOBAL';
  }
  if (role === 'TENANT_ADMIN' || role === 'TENANT_USER') {
    return sanitizeCodeToken(typeof scope.tenantId === 'function' ? scope.tenantId() : null, 'TENANT', 12);
  }
  return 'GLOBAL';
}

function generateRemediationCode(scope, remediationType) {
  const now = new Date();
  const ts = [
    now.getUTCFullYear(),
    String(now.getUTCMonth() + 1).padStart(2, '0'),
    String(now.getUTCDate()).padStart(2, '0'),
    String(now.getUTCHours()).padStart(2, '0'),
    String(now.getUTCMinutes()).padStart(2, '0'),
    String(now.getUTCSeconds()).padStart(2, '0')
  ].join('');
  const random = (window.crypto?.randomUUID?.() || Math.random().toString(16).slice(2))
    .replaceAll('-', '')
    .slice(0, 8)
    .toUpperCase();

  const scopeToken = resolveScopeToken(scope);
  const typeToken = sanitizeCodeToken(remediationType, 'GENERAL', 12);
  return `RM-${scopeToken}-${typeToken}-${ts}-${random}`;
}

function isCreateMode() {
  const editId = String(document.getElementById('edit_id')?.value || '').trim();
  return editId === '';
}

function applyAutoRemediationCode(scope) {
  const codeField = document.getElementById('remediation_code');
  if (!codeField) return;
  codeField.readOnly = true;
  if (!isCreateMode()) return;

  const remediationType = String(document.getElementById('remediation_type')?.value || 'USER_ACTION').trim() || 'USER_ACTION';
  codeField.value = generateRemediationCode(scope, remediationType);
}

function priorityMeaning(priority) {
  if (!Number.isFinite(priority) || priority < 1) {
    return 'Use a value from 1 to 1000. Lower numbers execute first.';
  }
  if (priority <= 25) return 'Very high priority remediation (runs early).';
  if (priority <= 100) return 'High priority remediation.';
  if (priority <= 500) return 'Normal priority remediation.';
  if (priority <= 1000) return 'Low priority remediation (runs later).';
  return 'Out of normal range. Use 1 to 1000.';
}

function updateRemediationGuidance() {
  const priority = toInt(document.getElementById('priority')?.value, 100);
  setHint('remediation_priority_hint', `Meaning: ${priorityMeaning(priority)}`);
  setHint(
    'remediation_priority_preview',
    `Ordering example: priority ${priority} executes before ${priority + 1} and after ${Math.max(1, priority - 1)}.`
  );
}

document.addEventListener('DOMContentLoaded', async () => {
  const nowIso = new Date().toISOString();
  const scope = await initPolicyScope();

  await populateLookupSelect('remediation_type', {
    lookupType: LOOKUP_TYPES.remediationType,
    fallbackOptions: REMEDIATION_TYPE_OPTIONS
  });
  await populateLookupSelect('os_type', {
    lookupType: LOOKUP_TYPES.osType,
    fallbackOptions: OS_TYPE_OPTIONS,
    allowedValues: OS_TYPE_OPTIONS,
    emptyOption: { value: '', label: '(any)' }
  });
  await populateLookupSelect('device_type', {
    lookupType: LOOKUP_TYPES.deviceType,
    fallbackOptions: DEVICE_TYPE_OPTIONS,
    emptyOption: { value: '', label: '(any)' }
  });
  await populateLookupSelect('status', {
    lookupType: LOOKUP_TYPES.recordStatus,
    fallbackOptions: STATUS_OPTIONS
  });

  const crud = mountDtCrud({
    tableSelector: '#remediationRulesTable',
    ajaxUrl: '/v1/ui/datatables/remediation-rules',
    idField: 'id',
    createUrl: '/v1/policies/remediation-rules',
    updateUrl: (id) => `/v1/policies/remediation-rules/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/remediation-rules/${encodeURIComponent(id)}`,
    requestHeaders: () => scope.getHeaders(),
    formId: 'remediationRulesForm',
    resetId: 'resetBtn',
    fieldIds: [
      'remediation_code', 'title', 'description', 'remediation_type',
      'os_type', 'device_type', 'priority', 'status',
      'effective_from', 'effective_to'
    ],
    defaults: () => ({
      remediation_code: generateRemediationCode(scope, 'USER_ACTION'),
      remediation_type: 'USER_ACTION',
      priority: 100,
      status: 'ACTIVE',
      effective_from: nowIso
    }),
    columns: [
      { data: 'id' },
      { data: 'remediation_code' },
      { data: 'remediation_type' },
      { data: 'os_type' },
      { data: 'device_type' },
      { data: 'status' },
      {
        data: null,
        orderable: false,
        render: (_value, _type, row) => renderPolicyActionButtons(scope, row)
      }
    ],
    canEditRow: (row) => canWritePolicyRow(scope, row),
    canDeleteRow: (row) => canWritePolicyRow(scope, row),
    onAction: async ({ act, row, id, requestHeaders, reload, reset }) => {
      if (act !== 'clone' || !shouldShowCloneToTenant(scope, row)) {
        return false;
      }
      const created = await clonePolicyRecord({
        id,
        getUrl: (rowId) => `/v1/policies/remediation-rules/${encodeURIComponent(rowId)}`,
        createUrl: '/v1/policies/remediation-rules',
        requestHeaders,
        entityLabel: 'remediation rule',
        transformPayload: (payload) => ({
          ...payload,
          // Let backend generate tenant-scoped remediation code.
          remediation_code: ''
        })
      });
      if (created?.id) {
        window.mdmToast?.(`Cloned to tenant (#${created.id})`);
      } else {
        window.mdmToast?.('Cloned to tenant');
      }
      reload();
      reset();
      return true;
    }
  });

  const priorityField = document.getElementById('priority');
  priorityField?.addEventListener('input', updateRemediationGuidance);
  priorityField?.addEventListener('change', updateRemediationGuidance);
  document.getElementById('remediation_type')?.addEventListener('change', () => applyAutoRemediationCode(scope));
  document.getElementById('remediation_type')?.addEventListener('input', () => applyAutoRemediationCode(scope));
  document.getElementById('resetBtn')?.addEventListener('click', () => {
    window.setTimeout(() => {
      applyAutoRemediationCode(scope);
      updateRemediationGuidance();
    }, 0);
  });
  document.querySelector('#remediationRulesTable')?.addEventListener('click', () => {
    window.setTimeout(() => {
      applyAutoRemediationCode(scope);
      updateRemediationGuidance();
    }, 0);
  });
  applyAutoRemediationCode(scope);
  updateRemediationGuidance();

  scope.onChange(() => {
    crud.reload();
    crud.reset();
    applyAutoRemediationCode(scope);
    updateRemediationGuidance();
  });
});
