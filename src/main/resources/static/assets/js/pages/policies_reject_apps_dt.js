import { mountDtCrud } from '../dt-crud.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';
import { initPolicyScope } from '../policy-scope.js';
import {
  canWritePolicyRow,
  clonePolicyRecord,
  renderPolicyActionButtons,
  shouldShowCloneToTenant
} from '../policy-row-actions.js';

const APP_OS_OPTIONS = ['ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX'];
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];
const SCORE_PREVIEW_BASELINES = [90, 70, 40];

const SEVERITY_LABELS = {
  1: '1 - Low risk impact',
  2: '2 - Guarded risk impact',
  3: '3 - Medium risk impact',
  4: '4 - High risk impact',
  5: '5 - Critical risk impact'
};

function toInt(value, fallback = 0) {
  const parsed = Number.parseInt(String(value ?? ''), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function clampScore(value) {
  if (value < 0) return 0;
  if (value > 100) return 100;
  return value;
}

function decisionForScore(score) {
  if (score < 40) return 'BLOCK';
  if (score < 60) return 'QUARANTINE';
  if (score < 80) return 'NOTIFY';
  return 'ALLOW';
}

function rejectDeltaForSeverity(severity) {
  return Math.max(-80, -10 * severity);
}

function setHint(id, text) {
  const node = document.getElementById(id);
  if (node) node.textContent = text;
}

function updateRejectAppGuidance() {
  const severity = toInt(document.getElementById('severity')?.value, 3);
  const safeSeverity = Math.min(5, Math.max(1, severity));
  const delta = rejectDeltaForSeverity(safeSeverity);

  setHint(
    'reject_severity_hint',
    `Meaning: ${SEVERITY_LABELS[safeSeverity] ?? 'Use 1 to 5 severity scale'}.`
  );
  setHint(
    'reject_preview_formula',
    `Fallback formula (when no Trust Policy override): delta = max(-80, -10 x severity) = ${delta}.`
  );
  const sampleOutcomes = SCORE_PREVIEW_BASELINES
    .map((before) => {
      const after = clampScore(before + delta);
      return `${before} -> ${after} (${decisionForScore(after)})`;
    })
    .join(' | ');
  setHint('reject_preview_examples', `Examples with current severity: ${sampleOutcomes}.`);
  setHint(
    'reject_preview_override_note',
    'Note: If a matching Trust Score Policy exists for this reject signal, weighted policy delta is used instead of this fallback.'
  );
}

document.addEventListener('DOMContentLoaded', async () => {
  const nowIso = new Date().toISOString();
  const scope = await initPolicyScope();

  await populateLookupSelect('app_os_type', {
    lookupType: LOOKUP_TYPES.osType,
    fallbackOptions: APP_OS_OPTIONS,
    allowedValues: APP_OS_OPTIONS
  });
  await populateLookupSelect('status', {
    lookupType: LOOKUP_TYPES.recordStatus,
    fallbackOptions: STATUS_OPTIONS
  });

  const crud = mountDtCrud({
    tableSelector: '#rejectAppsTable',
    ajaxUrl: '/v1/ui/datatables/reject-apps',
    idField: 'id',
    createUrl: '/v1/policies/reject-apps',
    updateUrl: (id) => `/v1/policies/reject-apps/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/reject-apps/${encodeURIComponent(id)}`,
    requestHeaders: () => scope.getHeaders(),
    formId: 'rejectAppsForm',
    resetId: 'resetBtn',
    fieldIds: [
      'policy_tag', 'threat_type', 'severity', 'blocked_reason',
      'app_os_type', 'app_category', 'app_name', 'publisher', 'package_id',
      'app_latest_version', 'min_allowed_version', 'status',
      'effective_from', 'effective_to'
    ],
    defaults: () => ({
      policy_tag: 'DEFAULT',
      threat_type: 'VPN',
      severity: 3,
      blocked_reason: 'Disallowed app category',
      app_os_type: 'ANDROID',
      app_category: 'VPN_PROXY',
      status: 'ACTIVE',
      effective_from: nowIso
    }),
    columns: [
      { data: 'id' },
      { data: 'app_os_type' },
      { data: 'app_name' },
      { data: 'package_id' },
      { data: 'severity' },
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
        getUrl: (rowId) => `/v1/policies/reject-apps/${encodeURIComponent(rowId)}`,
        createUrl: '/v1/policies/reject-apps',
        requestHeaders,
        entityLabel: 'reject app policy'
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

  const severityField = document.getElementById('severity');
  severityField?.addEventListener('input', updateRejectAppGuidance);
  severityField?.addEventListener('change', updateRejectAppGuidance);
  document.getElementById('resetBtn')?.addEventListener('click', () => {
    window.setTimeout(updateRejectAppGuidance, 0);
  });
  document.querySelector('#rejectAppsTable')?.addEventListener('click', () => {
    window.setTimeout(updateRejectAppGuidance, 0);
  });
  updateRejectAppGuidance();

  scope.onChange(() => {
    crud.reload();
    crud.reset();
    updateRejectAppGuidance();
  });
});
