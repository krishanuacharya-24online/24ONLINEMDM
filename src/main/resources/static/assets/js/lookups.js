import { apiFetch } from './api.js';

export const LOOKUP_TYPES = {
  recordStatus: 'lkp_record_status',
  osType: 'lkp_os_type',
  deviceType: 'lkp_device_type',
  complianceAction: 'lkp_compliance_action',
  matchMode: 'lkp_match_mode',
  ruleConditionOperator: 'lkp_rule_condition_operator',
  signalSource: 'lkp_signal_source',
  ruleRemediationSource: 'lkp_rule_remediation_source',
  remediationType: 'lkp_remediation_type',
  enforceMode: 'lkp_enforce_mode',
  scoreBand: 'lkp_score_band'
};

const remoteLookupCache = new Map();

function normalizeOption(value) {
  if (value == null) return null;
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    const text = String(value);
    return { value: text, label: text };
  }
  if (typeof value !== 'object') return null;
  const optionValue = value.value ?? value.code;
  if (optionValue == null) return null;
  const text = String(optionValue);
  const label = value.label ?? value.description ?? text;
  return { value: text, label: String(label) };
}

function normalizeOptions(options) {
  return (Array.isArray(options) ? options : [])
    .map(normalizeOption)
    .filter((option) => option && option.value !== '');
}

function filterOptions(options, allowedValues) {
  if (!Array.isArray(allowedValues) || !allowedValues.length) {
    return options;
  }
  const allowed = new Set(allowedValues.map((value) => String(value)));
  return options.filter((option) => allowed.has(String(option.value)));
}

export async function fetchLookupOptions(lookupType, fallbackOptions = []) {
  const fallback = normalizeOptions(fallbackOptions);
  if (!lookupType) {
    return fallback;
  }

  if (remoteLookupCache.has(lookupType)) {
    return remoteLookupCache.get(lookupType);
  }

  try {
    const response = await apiFetch(`/v1/lookups/${encodeURIComponent(lookupType)}`);
    const remote = normalizeOptions(response);
    if (remote.length) {
      remoteLookupCache.set(lookupType, remote);
      return remote;
    }
  } catch {
  }

  return fallback;
}

export async function populateLookupSelect(selectOrId, config = {}) {
  const {
    lookupType,
    fallbackOptions = [],
    emptyOption = null,
    selectedValue = undefined,
    allowedValues = null
  } = config;

  const select = typeof selectOrId === 'string' ? document.getElementById(selectOrId) : selectOrId;
  if (!select) return [];

  const currentValue = selectedValue === undefined ? String(select.value ?? '') : String(selectedValue ?? '');
  let options = await fetchLookupOptions(lookupType, fallbackOptions);
  options = filterOptions(options, allowedValues);

  if (!options.length) {
    options = filterOptions(normalizeOptions(fallbackOptions), allowedValues);
  }

  select.innerHTML = '';

  if (emptyOption && emptyOption.value !== undefined) {
    const empty = document.createElement('option');
    empty.value = String(emptyOption.value);
    empty.textContent = String(emptyOption.label ?? '');
    select.appendChild(empty);
  }

  options.forEach((option) => {
    const node = document.createElement('option');
    node.value = option.value;
    node.textContent = option.label;
    select.appendChild(node);
  });

  const hasCurrent = Array.from(select.options).some((option) => option.value === currentValue);
  if (hasCurrent) {
    select.value = currentValue;
  } else if (emptyOption && emptyOption.value !== undefined) {
    select.value = String(emptyOption.value);
  } else if (options.length) {
    select.value = options[0].value;
  } else {
    select.value = '';
  }

  return options;
}

export function clearLookupCache() {
  remoteLookupCache.clear();
}
