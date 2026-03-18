import { mountDtCrud } from '../dt-crud.js';
import { apiFetch } from '../api.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';
import { initPolicyScope } from '../policy-scope.js';
import {
  canWritePolicyRow,
  clonePolicyRecord,
  renderPolicyActionButtons,
  shouldShowCloneToTenant
} from '../policy-row-actions.js';

const SOURCE_TYPE_OPTIONS = ['SYSTEM_RULE', 'REJECT_APPLICATION', 'TRUST_POLICY', 'DECISION'];
const DECISION_ACTION_OPTIONS = ['ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY'];
const ENFORCE_MODE_OPTIONS = ['AUTO', 'MANUAL', 'ADVISORY'];
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];
const POLICY_REFERENCE_PAGE_SIZE = 50;
const REFERENCE_SEARCH_DEBOUNCE_MS = 250;
const referenceLabels = {
  systemRule: new Map(),
  rejectApp: new Map(),
  trustPolicy: new Map(),
  remediationRule: new Map()
};

const MAPPING_TARGET_FIELD = {
  SYSTEM_RULE: 'system_information_rule_id',
  REJECT_APPLICATION: 'reject_application_list_id',
  TRUST_POLICY: 'trust_score_policy_id',
  DECISION: 'decision_action'
};

const REFERENCE_SOURCE_CONFIG = {
  systemRule: {
    selectId: 'system_information_rule_id',
    endpoint: '/v1/ui/datatables/system-rules',
    emptyLabel: 'Select system rule',
    formatter: formatSystemRuleOption,
    labelMap: referenceLabels.systemRule,
    searchPlaceholder: 'Search system rules by code or tag'
  },
  rejectApp: {
    selectId: 'reject_application_list_id',
    endpoint: '/v1/ui/datatables/reject-apps',
    emptyLabel: 'Select reject app',
    formatter: formatRejectAppOption,
    labelMap: referenceLabels.rejectApp,
    searchPlaceholder: 'Search reject apps by app/package'
  },
  trustPolicy: {
    selectId: 'trust_score_policy_id',
    endpoint: '/v1/ui/datatables/trust-score-policies',
    emptyLabel: 'Select trust policy',
    formatter: formatTrustPolicyOption,
    labelMap: referenceLabels.trustPolicy,
    searchPlaceholder: 'Search trust policies by code or signal'
  },
  remediationRule: {
    selectId: 'remediation_rule_id',
    endpoint: '/v1/ui/datatables/remediation-rules',
    emptyLabel: 'Select remediation rule',
    formatter: formatRemediationOption,
    labelMap: referenceLabels.remediationRule,
    searchPlaceholder: 'Search remediation rules by code or title'
  }
};

function toInt(value, fallback = 1) {
  const parsed = Number.parseInt(String(value ?? ''), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function setHint(id, text) {
  const node = document.getElementById(id);
  if (node) node.textContent = text;
}

function clearField(id) {
  const node = document.getElementById(id);
  if (!node) return;
  if ((node.type || '').toLowerCase() === 'checkbox') {
    node.checked = false;
  } else {
    node.value = '';
  }
}

function setRequired(id, required) {
  const node = document.getElementById(id);
  if (node) node.required = required;
}

function setDisabled(id, disabled) {
  const node = document.getElementById(id);
  if (node) node.disabled = disabled;
}

function normalizeRows(rows) {
  return Array.isArray(rows) ? rows : [];
}

function toLongOrNull(value) {
  const parsed = Number.parseInt(String(value ?? '').trim(), 10);
  return Number.isFinite(parsed) ? parsed : null;
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function formatScopedLabel(row, label) {
  const scope = String(row?.tenant_id ?? row?.tenantId ?? '').trim();
  if (!scope) return `${label} [global]`;
  return `${label} [tenant:${scope}]`;
}

function formatSystemRuleOption(row) {
  const id = toLongOrNull(row?.id);
  if (id == null) return null;
  const code = String(row?.rule_code || row?.rule_tag || 'System rule').trim();
  const osType = String(row?.os_type || '').trim();
  const deviceType = String(row?.device_type || 'ANY').trim() || 'ANY';
  const status = String(row?.status || '').trim();
  const context = [osType, deviceType, status].filter(Boolean).join(', ');
  const base = `#${id} - ${code}${context ? ` (${context})` : ''}`;
  return {
    value: String(id),
    label: formatScopedLabel(row, base)
  };
}

function formatRejectAppOption(row) {
  const id = toLongOrNull(row?.id);
  if (id == null) return null;
  const appName = String(row?.app_name || 'Reject app').trim();
  const packageId = String(row?.package_id || '').trim();
  const osType = String(row?.app_os_type || '').trim();
  const status = String(row?.status || '').trim();
  const context = [packageId ? `pkg:${packageId}` : '', osType, status].filter(Boolean).join(', ');
  const base = `#${id} - ${appName}${context ? ` (${context})` : ''}`;
  return {
    value: String(id),
    label: formatScopedLabel(row, base)
  };
}

function formatTrustPolicyOption(row) {
  const id = toLongOrNull(row?.id);
  if (id == null) return null;
  const policyCode = String(row?.policy_code || 'Trust policy').trim();
  const sourceType = String(row?.source_type || '').trim();
  const signalKey = String(row?.signal_key || '').trim();
  const context = [sourceType, signalKey].filter(Boolean).join(':');
  const base = `#${id} - ${policyCode}${context ? ` (${context})` : ''}`;
  return {
    value: String(id),
    label: formatScopedLabel(row, base)
  };
}

function formatRemediationOption(row) {
  const id = toLongOrNull(row?.id);
  if (id == null) return null;
  const code = String(row?.remediation_code || row?.title || 'Remediation').trim();
  const remediationType = String(row?.remediation_type || '').trim();
  const status = String(row?.status || '').trim();
  const context = [remediationType, status].filter(Boolean).join(', ');
  const base = `#${id} - ${code}${context ? ` (${context})` : ''}`;
  return {
    value: String(id),
    label: formatScopedLabel(row, base)
  };
}

function buildReferenceOptions(rows, formatter) {
  return normalizeRows(rows)
    .map(formatter)
    .filter((entry) => entry && entry.value);
}

function refreshReferenceLabels(targetMap, options) {
  options.forEach((entry) => {
    targetMap.set(String(entry.value), String(entry.label || '').trim());
  });
}

function populateReferenceSelect(selectId, options, emptyLabel, labelMap) {
  const select = document.getElementById(selectId);
  if (!select) return;

  const currentValue = String(select.value || '');

  select.innerHTML = '';
  const empty = document.createElement('option');
  empty.value = '';
  empty.textContent = emptyLabel;
  select.appendChild(empty);

  options.forEach((entry) => {
    const node = document.createElement('option');
    node.value = entry.value;
    node.textContent = entry.label;
    select.appendChild(node);
  });

  if (currentValue && !options.some((entry) => entry.value === currentValue)) {
    const retained = document.createElement('option');
    retained.value = currentValue;
    retained.textContent = labelMap?.get(currentValue) || `#${currentValue}`;
    select.appendChild(retained);
  }

  const hasCurrent = options.some((entry) => entry.value === currentValue);
  select.value = hasCurrent ? currentValue : '';
  select.dispatchEvent(new Event('change', { bubbles: true }));
}

function renderNamedReference(map, value) {
  const id = toLongOrNull(value);
  if (id == null) {
    return '<span class="muted">-</span>';
  }
  const label = map.get(String(id));
  if (!label) {
    return `<span>#${id}</span>`;
  }
  const idPrefix = `#${id} - `;
  const prettyLabel = label.startsWith(idPrefix) ? label.slice(idPrefix.length) : label;
  return `<span>${escapeHtml(prettyLabel)}</span><span class="muted"> (#${id})</span>`;
}

function renderTrustPolicyOrDecisionReference(row) {
  const sourceType = String(row?.source_type || '').trim().toUpperCase();
  if (sourceType === 'DECISION') {
    const decision = String(row?.decision_action || '').trim().toUpperCase();
    if (!decision) {
      return '<span class="muted">-</span>';
    }
    return `<span>Decision: ${escapeHtml(decision)}</span>`;
  }
  return renderNamedReference(referenceLabels.trustPolicy, row?.trust_score_policy_id);
}

function toDebounced(fn, waitMs) {
  let timer = null;
  return (...args) => {
    if (timer) {
      clearTimeout(timer);
    }
    timer = window.setTimeout(() => fn(...args), waitMs);
  };
}

async function loadReferenceRows(scope, endpoint, search) {
  const url = new URL(endpoint, window.location.origin);
  url.searchParams.set('draw', '0');
  url.searchParams.set('start', '0');
  url.searchParams.set('length', String(POLICY_REFERENCE_PAGE_SIZE));
  if (search && String(search).trim()) {
    url.searchParams.set('search', String(search).trim());
  }
  const response = await apiFetch(`${url.pathname}${url.search}`, { headers: scope.getHeaders() });
  return normalizeRows(response?.data);
}

async function loadSingleReferenceOptions(scope, key, searchTerms = {}) {
  const config = REFERENCE_SOURCE_CONFIG[key];
  if (!config) return;
  const rows = await loadReferenceRows(scope, config.endpoint, searchTerms[key]).catch(() => []);
  const options = buildReferenceOptions(rows, config.formatter);
  refreshReferenceLabels(config.labelMap, options);
  populateReferenceSelect(config.selectId, options, config.emptyLabel, config.labelMap);
}

async function loadReferenceOptions(scope, searchTerms = {}) {
  const keys = Object.keys(REFERENCE_SOURCE_CONFIG);
  await Promise.all(keys.map((key) => loadSingleReferenceOptions(scope, key, searchTerms)));
}

function ensureSearchInput(selectId, placeholder) {
  const select = document.getElementById(selectId);
  if (!select || !select.parentElement) {
    return null;
  }
  const parent = select.parentElement;
  const existing = parent.querySelector(`input[data-ref-search-for="${selectId}"]`);
  if (existing) {
    return existing;
  }
  const input = document.createElement('input');
  input.type = 'search';
  input.className = 'mb-050';
  input.placeholder = placeholder;
  input.setAttribute('data-ref-search-for', selectId);
  input.setAttribute('aria-label', placeholder);
  parent.insertBefore(input, select);
  return input;
}

function attachReferenceSearch(scope, searchState) {
  Object.entries(REFERENCE_SOURCE_CONFIG).forEach(([key, config]) => {
    const input = ensureSearchInput(config.selectId, config.searchPlaceholder);
    if (!input || input.dataset.boundSearch === 'true') {
      return;
    }
    const refresh = toDebounced(async () => {
      searchState[key] = String(input.value || '').trim();
      await loadSingleReferenceOptions(scope, key, searchState);
    }, REFERENCE_SEARCH_DEBOUNCE_MS);
    input.addEventListener('input', refresh);
    input.dataset.boundSearch = 'true';
  });
}

function rankMeaning(rankOrder) {
  if (!Number.isFinite(rankOrder) || rankOrder < 1) {
    return 'Use a value from 1 to 1000. Lower rank executes first.';
  }
  if (rankOrder <= 10) return 'Very high precedence mapping.';
  if (rankOrder <= 100) return 'High precedence mapping.';
  if (rankOrder <= 500) return 'Normal precedence mapping.';
  if (rankOrder <= 1000) return 'Low precedence mapping.';
  return 'Out of normal range. Use 1 to 1000.';
}

function mappingTargetHint(sourceType) {
  if (sourceType === 'SYSTEM_RULE') {
    return 'Required target: System Rule ID only.';
  }
  if (sourceType === 'REJECT_APPLICATION') {
    return 'Required target: Reject App ID only.';
  }
  if (sourceType === 'TRUST_POLICY') {
    return 'Required target: Trust Policy ID only.';
  }
  if (sourceType === 'DECISION') {
    return 'Required target: Decision Action only.';
  }
  return 'Select source type to see required target field.';
}

function updateMappingGuidance({ clearIrrelevant = false } = {}) {
  const sourceType = String(document.getElementById('source_type')?.value ?? 'SYSTEM_RULE').trim() || 'SYSTEM_RULE';
  const rankOrder = toInt(document.getElementById('rank_order')?.value, 1);
  const enforceMode = String(document.getElementById('enforce_mode')?.value ?? 'ADVISORY').trim() || 'ADVISORY';
  const activeField = MAPPING_TARGET_FIELD[sourceType] || null;
  const targetFields = Object.values(MAPPING_TARGET_FIELD);

  setHint(
    'mapping_source_hint',
    'Meaning: Source type decides which one target selector must be populated. Keep all other target selectors empty.'
  );
  setHint('mapping_target_hint', `Current source: ${sourceType}. ${mappingTargetHint(sourceType)}`);
  setHint(
    'mapping_decision_hint',
    sourceType === 'DECISION'
      ? 'Decision action is required for DECISION source mappings.'
      : 'Leave decision action empty unless source type is DECISION.'
  );
  setHint(
    'mapping_enforce_hint',
    `Mode: ${enforceMode}. AUTO enforces automatically, MANUAL requires operator action, ADVISORY is guidance only.`
  );
  setHint('mapping_rank_hint', `Meaning: ${rankMeaning(rankOrder)}`);

  targetFields.forEach((fieldId) => setRequired(fieldId, fieldId === activeField));
  targetFields.forEach((fieldId) => setDisabled(fieldId, fieldId !== activeField));
  if (clearIrrelevant) {
    targetFields
      .filter((fieldId) => fieldId !== activeField)
      .forEach((fieldId) => clearField(fieldId));
  }
}

function normalizeMappingPayload(payload) {
  const sourceType = String(payload?.source_type || '').trim().toUpperCase();
  return {
    ...payload,
    system_information_rule_id: sourceType === 'SYSTEM_RULE' ? toLongOrNull(payload.system_information_rule_id) : null,
    reject_application_list_id: sourceType === 'REJECT_APPLICATION' ? toLongOrNull(payload.reject_application_list_id) : null,
    trust_score_policy_id: sourceType === 'TRUST_POLICY' ? toLongOrNull(payload.trust_score_policy_id) : null,
    decision_action: sourceType === 'DECISION' ? (payload.decision_action || null) : null,
    remediation_rule_id: toLongOrNull(payload.remediation_rule_id),
    rank_order: toInt(payload.rank_order, 1)
  };
}

document.addEventListener('DOMContentLoaded', async () => {
  const nowIso = new Date().toISOString();
  const scope = await initPolicyScope();
  const referenceSearchState = {
    systemRule: '',
    rejectApp: '',
    trustPolicy: '',
    remediationRule: ''
  };

  await populateLookupSelect('source_type', {
    lookupType: LOOKUP_TYPES.ruleRemediationSource,
    fallbackOptions: SOURCE_TYPE_OPTIONS
  });
  await populateLookupSelect('decision_action', {
    lookupType: LOOKUP_TYPES.complianceAction,
    fallbackOptions: DECISION_ACTION_OPTIONS,
    emptyOption: { value: '', label: '(none)' }
  });
  await populateLookupSelect('enforce_mode', {
    lookupType: LOOKUP_TYPES.enforceMode,
    fallbackOptions: ENFORCE_MODE_OPTIONS
  });
  await populateLookupSelect('status', {
    lookupType: LOOKUP_TYPES.recordStatus,
    fallbackOptions: STATUS_OPTIONS
  });
  attachReferenceSearch(scope, referenceSearchState);
  await loadReferenceOptions(scope, referenceSearchState);

  const crud = mountDtCrud({
    tableSelector: '#mappingsTable',
    ajaxUrl: '/v1/ui/datatables/rule-remediation-mappings',
    idField: 'id',
    createUrl: '/v1/policies/rule-remediation-mappings',
    updateUrl: (id) => `/v1/policies/rule-remediation-mappings/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/rule-remediation-mappings/${encodeURIComponent(id)}`,
    requestHeaders: () => scope.getHeaders(),
    formId: 'mappingsForm',
    resetId: 'resetBtn',
    fieldIds: [
      'source_type',
      'system_information_rule_id',
      'reject_application_list_id',
      'trust_score_policy_id',
      'decision_action',
      'remediation_rule_id',
      'enforce_mode',
      'rank_order',
      'status',
      'effective_from',
      'effective_to'
    ],
    defaults: () => ({
      source_type: 'SYSTEM_RULE',
      enforce_mode: 'ADVISORY',
      rank_order: 1,
      status: 'ACTIVE',
      effective_from: nowIso
    }),
    beforeSave: normalizeMappingPayload,
    columns: [
      { data: 'id' },
      { data: 'source_type' },
      {
        data: 'system_information_rule_id',
        render: (_value, _type, row) => renderNamedReference(referenceLabels.systemRule, row?.system_information_rule_id)
      },
      {
        data: 'reject_application_list_id',
        render: (_value, _type, row) => renderNamedReference(referenceLabels.rejectApp, row?.reject_application_list_id)
      },
      {
        data: 'trust_score_policy_id',
        render: (_value, _type, row) => renderTrustPolicyOrDecisionReference(row)
      },
      {
        data: 'remediation_rule_id',
        render: (_value, _type, row) => renderNamedReference(referenceLabels.remediationRule, row?.remediation_rule_id)
      },
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
        getUrl: (rowId) => `/v1/policies/rule-remediation-mappings/${encodeURIComponent(rowId)}`,
        createUrl: '/v1/policies/rule-remediation-mappings',
        requestHeaders,
        entityLabel: 'rule-to-remediation mapping'
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

  const sourceField = document.getElementById('source_type');
  sourceField?.addEventListener('change', () => updateMappingGuidance({ clearIrrelevant: true }));
  sourceField?.addEventListener('input', () => updateMappingGuidance({ clearIrrelevant: true }));
  document.getElementById('rank_order')?.addEventListener('input', () => updateMappingGuidance({ clearIrrelevant: false }));
  document.getElementById('rank_order')?.addEventListener('change', () => updateMappingGuidance({ clearIrrelevant: false }));
  document.getElementById('enforce_mode')?.addEventListener('change', () => updateMappingGuidance({ clearIrrelevant: false }));
  document.getElementById('resetBtn')?.addEventListener('click', () => {
    window.setTimeout(() => updateMappingGuidance({ clearIrrelevant: false }), 0);
  });
  document.querySelector('#mappingsTable')?.addEventListener('click', () => {
    window.setTimeout(() => updateMappingGuidance({ clearIrrelevant: false }), 0);
  });
  updateMappingGuidance({ clearIrrelevant: false });

  scope.onChange(async () => {
    Object.keys(referenceSearchState).forEach((key) => {
      referenceSearchState[key] = '';
    });
    Object.values(REFERENCE_SOURCE_CONFIG).forEach((config) => {
      const input = document.querySelector(`input[data-ref-search-for="${config.selectId}"]`);
      if (input) input.value = '';
    });
    await loadReferenceOptions(scope, referenceSearchState);
    crud.reload();
    crud.reset();
    updateMappingGuidance({ clearIrrelevant: false });
  });
});
