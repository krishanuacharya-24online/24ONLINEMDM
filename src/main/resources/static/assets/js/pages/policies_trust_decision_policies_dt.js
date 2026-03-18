import { mountDtCrud } from '../dt-crud.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';
import { initPolicyScope } from '../policy-scope.js';
import {
  canWritePolicyRow,
  clonePolicyRecord,
  renderPolicyActionButtons,
  shouldShowCloneToTenant
} from '../policy-row-actions.js';

const DECISION_ACTION_OPTIONS = ['ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY'];
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];
const SCORE_PREVIEW_SAMPLES = [20, 40, 60, 80];

function toInt(value, fallback = 0) {
  const parsed = Number.parseInt(String(value ?? ''), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function setHint(id, text) {
  const node = document.getElementById(id);
  if (node) node.textContent = text;
}

function updateDecisionPolicyGuidance() {
  const scoreMin = toInt(document.getElementById('score_min')?.value, 0);
  const scoreMax = toInt(document.getElementById('score_max')?.value, 100);
  const action = String(document.getElementById('decision_action')?.value ?? 'ALLOW').trim() || 'ALLOW';

  setHint('decision_score_min_hint', 'Meaning: Inclusive lower bound (0..100) for this decision policy.');
  setHint('decision_score_max_hint', 'Meaning: Inclusive upper bound (0..100) for this decision policy.');

  if (scoreMin > scoreMax) {
    setHint('decision_range_preview_formula', `Invalid range: [${scoreMin}..${scoreMax}]`);
    setHint('decision_range_preview_examples', 'No score can match this range.');
    setHint('decision_range_preview_warning', 'Save will fail because score_min must be less than or equal to score_max.');
    return;
  }

  const width = scoreMax - scoreMin + 1;
  const coverage = Math.round((width / 101) * 100);
  setHint(
    'decision_range_preview_formula',
    `Range: [${scoreMin}..${scoreMax}] (inclusive), action: ${action}, coverage: ${width} score values (~${coverage}%).`
  );

  const sampleMatches = SCORE_PREVIEW_SAMPLES
    .map((score) => `${score}:${score >= scoreMin && score <= scoreMax ? action : 'no-match'}`)
    .join(' | ');
  setHint('decision_range_preview_examples', `Sample scores: ${sampleMatches}.`);
  setHint(
    'decision_range_preview_warning',
    'Tip: Keep active ranges non-overlapping for predictable outcomes. If ranges overlap, the most specific/higher-min range wins first.'
  );
}

document.addEventListener('DOMContentLoaded', async () => {
  const nowIso = new Date().toISOString();
  const scope = await initPolicyScope();

  await Promise.all([
    populateLookupSelect('decision_action', {
      lookupType: LOOKUP_TYPES.complianceAction,
      fallbackOptions: DECISION_ACTION_OPTIONS
    }),
    populateLookupSelect('status', {
      lookupType: LOOKUP_TYPES.recordStatus,
      fallbackOptions: STATUS_OPTIONS
    })
  ]);

  const crud = mountDtCrud({
    tableSelector: '#decisionPoliciesTable',
    ajaxUrl: '/v1/ui/datatables/trust-decision-policies',
    idField: 'id',
    createUrl: '/v1/policies/trust-decision-policies',
    updateUrl: (id) => `/v1/policies/trust-decision-policies/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/trust-decision-policies/${encodeURIComponent(id)}`,
    requestHeaders: () => scope.getHeaders(),
    formId: 'decisionPoliciesForm',
    resetId: 'resetBtn',
    fieldIds: [
      'policy_name', 'score_min', 'score_max', 'decision_action',
      'remediation_required', 'response_message',
      'status', 'effective_from', 'effective_to'
    ],
    defaults: () => ({
      score_min: 0,
      score_max: 100,
      decision_action: 'ALLOW',
      remediation_required: false,
      status: 'ACTIVE',
      effective_from: nowIso
    }),
    columns: [
      { data: 'id' },
      { data: 'policy_name' },
      { data: 'score_min' },
      { data: 'score_max' },
      { data: 'decision_action' },
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
        getUrl: (rowId) => `/v1/policies/trust-decision-policies/${encodeURIComponent(rowId)}`,
        createUrl: '/v1/policies/trust-decision-policies',
        requestHeaders,
        entityLabel: 'trust decision policy'
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

  ['score_min', 'score_max', 'decision_action'].forEach((id) => {
    const field = document.getElementById(id);
    field?.addEventListener('input', updateDecisionPolicyGuidance);
    field?.addEventListener('change', updateDecisionPolicyGuidance);
  });
  document.getElementById('resetBtn')?.addEventListener('click', () => {
    window.setTimeout(updateDecisionPolicyGuidance, 0);
  });
  document.querySelector('#decisionPoliciesTable')?.addEventListener('click', () => {
    window.setTimeout(updateDecisionPolicyGuidance, 0);
  });
  updateDecisionPolicyGuidance();

  scope.onChange(() => {
    crud.reload();
    crud.reset();
    updateDecisionPolicyGuidance();
  });
});
