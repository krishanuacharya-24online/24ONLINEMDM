import { mountDtCrud } from '../dt-crud.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';
import { initPolicyScope } from '../policy-scope.js';
import {
  canWritePolicyRow,
  clonePolicyRecord,
  renderPolicyActionButtons,
  shouldShowCloneToTenant
} from '../policy-row-actions.js';

const SOURCE_TYPE_OPTIONS = ['SYSTEM_RULE', 'REJECT_APPLICATION', 'POSTURE_SIGNAL', 'MANUAL'];
const COMPLIANCE_ACTION_OPTIONS = ['ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY'];
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];
const SCORE_PREVIEW_BASELINES = [90, 70, 40];

function toFloat(value, fallback = 0) {
  const parsed = Number.parseFloat(String(value ?? '').trim());
  return Number.isFinite(parsed) ? parsed : fallback;
}

function clampScore(value) {
  if (value < 0) return 0;
  if (value > 100) return 100;
  return value;
}

function clampWeightedDelta(value) {
  if (value > 1000) return 1000;
  if (value < -1000) return -1000;
  return value;
}

function decisionForScore(score) {
  if (score < 40) return 'BLOCK';
  if (score < 60) return 'QUARANTINE';
  if (score < 80) return 'NOTIFY';
  return 'ALLOW';
}

function setHint(id, text) {
  const node = document.getElementById(id);
  if (node) node.textContent = text;
}

function formatSigned(value) {
  return value > 0 ? `+${value}` : String(value);
}

function updateTrustPolicyGuidance() {
  const severityRaw = String(document.getElementById('severity')?.value ?? '').trim();
  const scoreDelta = toFloat(document.getElementById('score_delta')?.value, -10);
  const weight = toFloat(document.getElementById('weight')?.value, 1);

  const weighted = clampWeightedDelta(Math.round(scoreDelta * weight));
  const severityHint = severityRaw === ''
    ? 'Meaning: Any severity (policy can match all severities for the source and signal key).'
    : `Meaning: Applies only to severity ${severityRaw} matches for this signal.`;

  setHint('trust_policy_severity_hint', severityHint);
  setHint(
    'trust_policy_delta_hint',
    `Meaning: Base score change is ${formatSigned(Math.round(scoreDelta))}. Negative reduces trust; positive increases trust.`
  );
  setHint(
    'trust_policy_weight_hint',
    `Meaning: Weight ${weight} multiplies score delta. Effective delta = round(score_delta x weight).`
  );
  setHint(
    'trust_policy_preview_formula',
    `Weighted formula: effective_delta = clamp(round(${scoreDelta} x ${weight}), -1000..1000) = ${formatSigned(weighted)}.`
  );

  const samples = SCORE_PREVIEW_BASELINES
    .map((before) => {
      const after = clampScore(before + weighted);
      return `${before} -> ${after} (${decisionForScore(after)})`;
    })
    .join(' | ');
  setHint('trust_policy_preview_examples', `Examples with current weighted delta: ${samples}.`);
  setHint(
    'trust_policy_preview_note',
    'This policy delta overrides fallback deltas from matched System Rules, Reject Apps, or Lifecycle signals.'
  );
}

document.addEventListener('DOMContentLoaded', async () => {
  const nowIso = new Date().toISOString();
  const scope = await initPolicyScope();

  await Promise.all([
    populateLookupSelect('source_type', {
      lookupType: LOOKUP_TYPES.signalSource,
      fallbackOptions: SOURCE_TYPE_OPTIONS
    }),
    populateLookupSelect('compliance_action', {
      lookupType: LOOKUP_TYPES.complianceAction,
      fallbackOptions: COMPLIANCE_ACTION_OPTIONS,
      emptyOption: { value: '', label: '(none)' }
    }),
    populateLookupSelect('status', {
      lookupType: LOOKUP_TYPES.recordStatus,
      fallbackOptions: STATUS_OPTIONS
    })
  ]);

  const crud = mountDtCrud({
    tableSelector: '#trustPoliciesTable',
    ajaxUrl: '/v1/ui/datatables/trust-score-policies',
    idField: 'id',
    createUrl: '/v1/policies/trust-score-policies',
    updateUrl: (id) => `/v1/policies/trust-score-policies/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/trust-score-policies/${encodeURIComponent(id)}`,
    requestHeaders: () => scope.getHeaders(),
    formId: 'trustPoliciesForm',
    resetId: 'resetBtn',
    fieldIds: [
      'policy_code', 'source_type', 'signal_key', 'severity',
      'compliance_action', 'score_delta', 'weight',
      'status', 'effective_from', 'effective_to'
    ],
    defaults: () => ({
      source_type: 'SYSTEM_RULE',
      score_delta: -10,
      weight: 1.0,
      status: 'ACTIVE',
      effective_from: nowIso
    }),
    columns: [
      { data: 'id' },
      { data: 'policy_code' },
      { data: 'source_type' },
      { data: 'signal_key' },
      { data: 'score_delta' },
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
        getUrl: (rowId) => `/v1/policies/trust-score-policies/${encodeURIComponent(rowId)}`,
        createUrl: '/v1/policies/trust-score-policies',
        requestHeaders,
        entityLabel: 'trust score policy'
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

  ['severity', 'score_delta', 'weight'].forEach((id) => {
    const field = document.getElementById(id);
    field?.addEventListener('input', updateTrustPolicyGuidance);
    field?.addEventListener('change', updateTrustPolicyGuidance);
  });
  document.getElementById('resetBtn')?.addEventListener('click', () => {
    window.setTimeout(updateTrustPolicyGuidance, 0);
  });
  document.querySelector('#trustPoliciesTable')?.addEventListener('click', () => {
    window.setTimeout(updateTrustPolicyGuidance, 0);
  });
  updateTrustPolicyGuidance();

  scope.onChange(() => {
    crud.reload();
    crud.reset();
    updateTrustPolicyGuidance();
  });
});
