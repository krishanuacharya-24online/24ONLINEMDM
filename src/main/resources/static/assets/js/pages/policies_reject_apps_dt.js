import { apiFetch, apiFetchAllPages } from '../api.js';
import { mountDtCrud } from '../dt-crud.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';
import { initPolicyScope } from '../policy-scope.js';
import {
  canWritePolicyRow,
  clonePolicyRecord,
  renderPolicyActionButtons,
  shouldShowCloneToTenant
} from '../policy-row-actions.js';

const APP_OS_OPTIONS = ['ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'];
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];
const SCORE_PREVIEW_BASELINES = [90, 70, 40];
const REQUIRED_TEXT_FIELDS = {
  policy_tag: 'Policy tag',
  app_name: 'App name',
  min_allowed_version: 'Min allowed version'
};
const SELECT_FIELDS = {
  app_os_type: 'App OS type',
  status: 'Status'
};
const CUSTOM_VALIDATED_FIELDS = [
  'policy_tag',
  'severity',
  'app_os_type',
  'app_name',
  'min_allowed_version',
  'effective_from',
  'effective_to'
];

const SEVERITY_LABELS = {
  1: '1 - Low risk impact',
  2: '2 - Guarded risk impact',
  3: '3 - Medium risk impact',
  4: '4 - High risk impact',
  5: '5 - Critical risk impact'
};

const catalogCache = new Map();
let activeCatalogEntries = [];

function getField(id) {
  return document.getElementById(id);
}

function currentDateValue() {
  return new Date().toISOString().slice(0, 10);
}

function defaultRejectAppValues() {
  return {
    policy_tag: 'DEFAULT',
    severity: 3,
    app_os_type: 'ANDROID',
    app_name: '',
    publisher: '',
    package_id: '',
    min_allowed_version: '',
    status: 'ACTIVE',
    effective_from: currentDateValue(),
    effective_to: ''
  };
}

function trimmedValue(input) {
  return String(input?.value ?? '').trim();
}

function normalizeOptionalText(value) {
  const normalized = String(value ?? '').trim();
  return normalized || null;
}

function normalizeOptionalCatalogText(value) {
  return String(value ?? '').trim().toLowerCase();
}

function normalizeSeverity(value) {
  const parsed = Number.parseInt(String(value ?? ''), 10);
  return Number.isFinite(parsed) ? parsed : null;
}

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
  const node = getField(id);
  if (node) {
    node.textContent = text;
  }
}

function updateRejectAppGuidance() {
  const severity = toInt(getField('severity')?.value, 3);
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

function setFieldValidity(input, message = '') {
  if (!(input instanceof HTMLElement)) {
    return;
  }
  if (message) {
    input.setAttribute('aria-invalid', 'true');
    input.dataset.validationMessage = message;
  } else {
    input.removeAttribute('aria-invalid');
    delete input.dataset.validationMessage;
  }
}

function clearFormError() {
  const errorNode = getField('rejectAppsFormError');
  if (!errorNode) {
    return;
  }
  errorNode.hidden = true;
  errorNode.textContent = '';
}

function setFormError(message) {
  const errorNode = getField('rejectAppsFormError');
  if (!errorNode) {
    return;
  }
  errorNode.textContent = message;
  errorNode.hidden = !message;
}

function validateRequiredTextField(id, label) {
  const input = getField(id);
  if (!(input instanceof HTMLInputElement || input instanceof HTMLTextAreaElement)) {
    return;
  }
  const value = trimmedValue(input);
  setFieldValidity(input, value ? '' : `${label} is required.`);
}

function validateSelectField(id, label) {
  const input = getField(id);
  if (!(input instanceof HTMLSelectElement)) {
    return;
  }
  setFieldValidity(input, trimmedValue(input) ? '' : `${label} is required.`);
}

function validateSeverityField() {
  const input = getField('severity');
  if (!(input instanceof HTMLSelectElement)) {
    return;
  }
  const value = normalizeSeverity(input.value);
  let message = '';
  if (value == null) {
    message = 'Severity is required.';
  } else if (value < 1 || value > 5) {
    message = 'Severity must be between 1 and 5.';
  }
  setFieldValidity(input, message);
}

function validateEffectiveWindow() {
  const fromInput = getField('effective_from');
  const toInput = getField('effective_to');
  if (!(fromInput instanceof HTMLInputElement) || !(toInput instanceof HTMLInputElement)) {
    return;
  }

  const fromValue = trimmedValue(fromInput);
  const toValue = trimmedValue(toInput);
  setFieldValidity(fromInput, '');
  setFieldValidity(toInput, '');

  if (!toValue) {
    return;
  }
  if (!fromValue) {
    setFieldValidity(fromInput, 'Effective from is required when effective to is set.');
    return;
  }

  const fromTime = Date.parse(`${fromValue}T00:00:00Z`);
  const toTime = Date.parse(`${toValue}T00:00:00Z`);
  if (Number.isNaN(fromTime) || Number.isNaN(toTime)) {
    setFieldValidity(toInput, 'Enter valid effective dates.');
    return;
  }
  if (toTime <= fromTime) {
    setFieldValidity(toInput, 'Effective to must be later than effective from.');
  }
}

function validateRejectAppField(id) {
  if (REQUIRED_TEXT_FIELDS[id]) {
    validateRequiredTextField(id, REQUIRED_TEXT_FIELDS[id]);
    return;
  }
  if (SELECT_FIELDS[id]) {
    validateSelectField(id, SELECT_FIELDS[id]);
    return;
  }
  if (id === 'severity') {
    validateSeverityField();
    return;
  }
  if (id === 'effective_from' || id === 'effective_to') {
    validateEffectiveWindow();
  }
}

function resetRejectAppValidation() {
  clearFormError();
  CUSTOM_VALIDATED_FIELDS.forEach((fieldId) => setFieldValidity(getField(fieldId), ''));
}

function firstInvalidField() {
  return CUSTOM_VALIDATED_FIELDS
    .map((fieldId) => getField(fieldId))
    .find((field) => field instanceof HTMLElement && field.getAttribute('aria-invalid') === 'true') || null;
}

function validateRejectAppForm() {
  Object.entries(REQUIRED_TEXT_FIELDS).forEach(([fieldId, label]) => {
    validateRequiredTextField(fieldId, label);
  });
  Object.entries(SELECT_FIELDS).forEach(([fieldId, label]) => {
    validateSelectField(fieldId, label);
  });
  validateSeverityField();
  validateEffectiveWindow();

  const invalidField = firstInvalidField();
  if (!invalidField) {
    clearFormError();
    return null;
  }
  setFormError(invalidField.dataset.validationMessage || 'Review the highlighted fields and try again.');
  return invalidField;
}

function normalizeCatalogRows(payload) {
  if (Array.isArray(payload)) {
    return payload;
  }
  if (Array.isArray(payload?.content)) {
    return payload.content;
  }
  if (Array.isArray(payload?.data)) {
    return payload.data;
  }
  return [];
}

async function fetchCatalogApplications(osType, { forceReload = false } = {}) {
  const normalizedOsType = String(osType || '').trim().toUpperCase();
  if (!normalizedOsType) {
    return [];
  }
  if (!forceReload && catalogCache.has(normalizedOsType)) {
    return catalogCache.get(normalizedOsType);
  }

  const rows = (await apiFetchAllPages(`/v1/catalog/applications?os_type=${encodeURIComponent(normalizedOsType)}`, {
    pageSize: 100,
    normalizeRows: normalizeCatalogRows
  }))
    .filter((entry) => entry?.id != null)
    .sort((left, right) => {
      const leftName = String(left?.app_name ?? left?.appName ?? '').trim().toLowerCase();
      const rightName = String(right?.app_name ?? right?.appName ?? '').trim().toLowerCase();
      const byName = leftName.localeCompare(rightName);
      if (byName !== 0) {
        return byName;
      }
      const leftPackage = String(left?.package_id ?? left?.packageId ?? '').trim().toLowerCase();
      const rightPackage = String(right?.package_id ?? right?.packageId ?? '').trim().toLowerCase();
      return leftPackage.localeCompare(rightPackage);
    });
  catalogCache.set(normalizedOsType, rows);
  return rows;
}

function catalogOptionLabel(entry) {
  const appName = String(entry?.app_name ?? entry?.appName ?? '').trim() || 'Unnamed app';
  const packageId = String(entry?.package_id ?? entry?.packageId ?? '').trim();
  const publisher = String(entry?.publisher ?? '').trim();
  const parts = [appName];
  if (packageId) {
    parts.push(packageId);
  }
  if (publisher) {
    parts.push(publisher);
  }
  return parts.join(' | ');
}

function selectedCatalogEntry() {
  const picker = getField('catalog_app_picker');
  if (!(picker instanceof HTMLSelectElement)) {
    return null;
  }
  const selectedId = Number.parseInt(String(picker.value || ''), 10);
  if (!Number.isFinite(selectedId)) {
    return null;
  }
  return activeCatalogEntries.find((entry) => Number(entry?.id) === selectedId) || null;
}

function updateCatalogMeta(entry = selectedCatalogEntry()) {
  const meta = getField('catalog_app_meta');
  if (!meta) {
    return;
  }
  if (!entry) {
    meta.textContent = 'Pick a catalog app to prefill OS, app name, publisher, and package ID.';
    return;
  }
  const osType = String(entry?.os_type ?? entry?.osType ?? '').trim() || 'Unknown OS';
  const appName = String(entry?.app_name ?? entry?.appName ?? '').trim() || 'Unnamed app';
  const packageId = String(entry?.package_id ?? entry?.packageId ?? '').trim() || 'No package ID';
  const publisher = String(entry?.publisher ?? '').trim() || 'Unknown publisher';
  meta.textContent = `Catalog match: ${appName} | ${packageId} | ${publisher} | ${osType}`;
}

function formCatalogIdentity() {
  return {
    osType: normalizeOptionalCatalogText(getField('app_os_type')?.value),
    appName: normalizeOptionalCatalogText(getField('app_name')?.value),
    packageId: normalizeOptionalCatalogText(getField('package_id')?.value),
    publisher: normalizeOptionalCatalogText(getField('publisher')?.value)
  };
}

function findCatalogMatchForForm() {
  const current = formCatalogIdentity();
  if (!current.osType || !current.appName) {
    return null;
  }

  let bestMatch = null;
  let bestScore = -1;
  activeCatalogEntries.forEach((entry) => {
    const entryOsType = normalizeOptionalCatalogText(entry?.os_type ?? entry?.osType);
    const entryAppName = normalizeOptionalCatalogText(entry?.app_name ?? entry?.appName);
    const entryPackageId = normalizeOptionalCatalogText(entry?.package_id ?? entry?.packageId);
    const entryPublisher = normalizeOptionalCatalogText(entry?.publisher);
    if (entryOsType !== current.osType || entryAppName !== current.appName) {
      return;
    }
    let score = 1;
    if (current.packageId && entryPackageId === current.packageId) {
      score += 2;
    }
    if (current.publisher && entryPublisher === current.publisher) {
      score += 1;
    }
    if (!current.packageId && !entryPackageId) {
      score += 1;
    }
    if (score > bestScore) {
      bestScore = score;
      bestMatch = entry;
    }
  });
  return bestMatch;
}

function syncCatalogSelectionWithForm() {
  const picker = getField('catalog_app_picker');
  if (!(picker instanceof HTMLSelectElement)) {
    return;
  }
  const match = findCatalogMatchForForm();
  picker.value = match?.id != null ? String(match.id) : '';
  updateCatalogMeta(match);
}

async function loadCatalogOptions({ forceReload = false } = {}) {
  const picker = getField('catalog_app_picker');
  if (!(picker instanceof HTMLSelectElement)) {
    return;
  }
  const osType = String(getField('app_os_type')?.value || '').trim().toUpperCase();
  picker.disabled = true;
  picker.innerHTML = '<option value="">Loading catalog apps...</option>';

  if (!osType) {
    activeCatalogEntries = [];
    picker.innerHTML = '<option value="">Select app OS type first</option>';
    picker.disabled = false;
    updateCatalogMeta(null);
    return;
  }

  try {
    activeCatalogEntries = await fetchCatalogApplications(osType, { forceReload });
    picker.innerHTML = '';
    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = activeCatalogEntries.length
      ? 'Select catalog app'
      : `No catalog apps found for ${osType}`;
    picker.appendChild(placeholder);

    activeCatalogEntries.forEach((entry) => {
      const option = document.createElement('option');
      option.value = String(entry.id);
      option.textContent = catalogOptionLabel(entry);
      picker.appendChild(option);
    });

    syncCatalogSelectionWithForm();
  } catch (error) {
    activeCatalogEntries = [];
    picker.innerHTML = '<option value="">Failed to load catalog apps</option>';
    updateCatalogMeta(null);
    window.mdmToast?.(`Unable to load catalog apps: ${error?.message || 'Unknown error'}`);
  } finally {
    picker.disabled = false;
  }
}

async function applySelectedCatalogEntry() {
  const entry = selectedCatalogEntry();
  if (!entry) {
    window.mdmToast?.('Select a catalog app first.');
    return;
  }

  const osTypeInput = getField('app_os_type');
  if (osTypeInput instanceof HTMLSelectElement) {
    osTypeInput.value = String(entry?.os_type ?? entry?.osType ?? '').trim();
  }
  const appNameInput = getField('app_name');
  if (appNameInput instanceof HTMLInputElement) {
    appNameInput.value = String(entry?.app_name ?? entry?.appName ?? '').trim();
  }
  const publisherInput = getField('publisher');
  if (publisherInput instanceof HTMLInputElement) {
    publisherInput.value = String(entry?.publisher ?? '').trim();
  }
  const packageInput = getField('package_id');
  if (packageInput instanceof HTMLInputElement) {
    packageInput.value = String(entry?.package_id ?? entry?.packageId ?? '').trim();
  }

  ['app_os_type', 'app_name'].forEach((fieldId) => validateRejectAppField(fieldId));
  clearFormError();
  syncCatalogSelectionWithForm();
  window.mdmToast?.('Catalog app applied to the form.');
}

document.addEventListener('DOMContentLoaded', async () => {
  const form = getField('rejectAppsForm');
  if (!(form instanceof HTMLFormElement)) {
    return;
  }
  form.noValidate = true;

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
      'policy_tag',
      'severity',
      'app_os_type',
      'app_name',
      'publisher',
      'package_id',
      'min_allowed_version',
      'status',
      'effective_from',
      'effective_to'
    ],
    defaults: () => defaultRejectAppValues(),
    beforeSave: (payload) => ({
      policy_tag: normalizeOptionalText(payload?.policy_tag),
      severity: normalizeSeverity(payload?.severity),
      app_os_type: normalizeOptionalText(payload?.app_os_type),
      app_name: normalizeOptionalText(payload?.app_name),
      publisher: normalizeOptionalText(payload?.publisher),
      package_id: normalizeOptionalText(payload?.package_id),
      min_allowed_version: normalizeOptionalText(payload?.min_allowed_version),
      status: normalizeOptionalText(payload?.status),
      effective_from: payload?.effective_from ?? null,
      effective_to: payload?.effective_to ?? null
    }),
    loadEditRow: async ({ id, requestHeaders }) => apiFetch(`/v1/policies/reject-apps/${encodeURIComponent(id)}`, {
      headers: requestHeaders()
    }),
    afterFill: async () => {
      resetRejectAppValidation();
      updateRejectAppGuidance();
      await loadCatalogOptions();
    },
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
      await reset();
      return true;
    }
  });

  form.addEventListener('submit', (event) => {
    const firstInvalid = validateRejectAppForm();
    if (!firstInvalid) {
      return;
    }
    event.preventDefault();
    event.stopImmediatePropagation();
    if (typeof firstInvalid.focus === 'function') {
      firstInvalid.focus();
    }
  }, true);

  CUSTOM_VALIDATED_FIELDS.forEach((fieldId) => {
    const input = getField(fieldId);
    input?.addEventListener('input', () => {
      validateRejectAppField(fieldId);
      clearFormError();
    });
    input?.addEventListener('change', () => {
      validateRejectAppField(fieldId);
      clearFormError();
    });
  });

  ['app_name', 'publisher', 'package_id'].forEach((fieldId) => {
    const input = getField(fieldId);
    input?.addEventListener('input', () => {
      syncCatalogSelectionWithForm();
    });
    input?.addEventListener('change', () => {
      syncCatalogSelectionWithForm();
    });
  });

  const severityField = getField('severity');
  severityField?.addEventListener('input', updateRejectAppGuidance);
  severityField?.addEventListener('change', updateRejectAppGuidance);

  const osTypeField = getField('app_os_type');
  osTypeField?.addEventListener('change', async () => {
    validateRejectAppField('app_os_type');
    clearFormError();
    await loadCatalogOptions();
  });

  getField('catalog_app_picker')?.addEventListener('change', () => {
    updateCatalogMeta();
  });

  getField('catalogAppApplyBtn')?.addEventListener('click', async () => {
    await applySelectedCatalogEntry();
  });

  getField('catalogAppReloadBtn')?.addEventListener('click', async () => {
    await loadCatalogOptions({ forceReload: true });
    window.mdmToast?.('Catalog apps reloaded.');
  });

  updateRejectAppGuidance();

  scope.onChange(async () => {
    crud.reload();
    await crud.reset();
  });
});
