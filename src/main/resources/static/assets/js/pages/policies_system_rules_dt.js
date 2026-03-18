import { apiFetch } from '../api.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';
import { initPolicyScope } from '../policy-scope.js';
import {
  canWritePolicyRow,
  renderPolicyActionButtons,
  shouldShowCloneToTenant
} from '../policy-row-actions.js';

const OS_TYPE_OPTIONS = ['ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'];
const DEVICE_TYPE_OPTIONS = ['PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER'];
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];
const MATCH_MODE_OPTIONS = ['ALL', 'ANY'];
const COMPLIANCE_ACTION_OPTIONS = ['ALLOW', 'QUARANTINE', 'BLOCK', 'NOTIFY'];
const SCORE_PREVIEW_BASELINES = [90, 70, 40];

const SEVERITY_LABELS = {
  1: '1 - Low risk impact',
  2: '2 - Guarded risk impact',
  3: '3 - Medium risk impact',
  4: '4 - High risk impact',
  5: '5 - Critical risk impact'
};

async function confirmDanger(message, title = 'Confirm deletion') {
  if (typeof window.mdmConfirm === 'function') {
    return window.mdmConfirm({
      title,
      message,
      confirmLabel: 'Delete',
      cancelLabel: 'Cancel',
      danger: true
    });
  }
  return window.confirm(message);
}

async function cloneSystemRuleToTenant(id, requestHeaders) {
  const numericId = Number.parseInt(String(id || ''), 10);
  if (!Number.isFinite(numericId)) {
    throw new Error('Invalid system rule id for cloning');
  }

  const prompt = `Clone system rule #${numericId} to your tenant scope?`;
  const confirmed = await (typeof window.mdmConfirm === 'function'
    ? window.mdmConfirm({
      title: 'Confirm clone',
      message: prompt,
      confirmLabel: 'Clone',
      cancelLabel: 'Cancel',
      danger: false
    })
    : window.confirm(prompt));
  if (!confirmed) {
    return null;
  }

  const result = await apiFetch(`/v1/policies/system-rules/${encodeURIComponent(numericId)}/clone`, {
    method: 'POST',
    headers: requestHeaders()
  });

  const createdId = result?.rule?.id ?? result?.rule?.rule_id ?? null;
  return {
    createdId,
    clonedConditions: Number.parseInt(String(result?.clonedConditions ?? result?.cloned_conditions ?? 0), 10) || 0
  };
}

function formJson() {
  return {
    rule_code: document.getElementById('rule_code').value.trim(),
    rule_tag: document.getElementById('rule_tag').value.trim(),
    os_type: document.getElementById('os_type').value,
    device_type: document.getElementById('device_type').value || null,
    status: document.getElementById('status').value,
    severity: Number(document.getElementById('severity').value),
    priority: Number(document.getElementById('priority').value),
    version: Number(document.getElementById('version').value),
    match_mode: document.getElementById('match_mode').value,
    compliance_action: document.getElementById('compliance_action').value,
    risk_score_delta: Number(document.getElementById('risk_score_delta').value),
    description: document.getElementById('description').value || null,
    effective_from: new Date().toISOString()
  };
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

function formatSigned(value) {
  return value > 0 ? `+${value}` : String(value);
}

function severityHint(severity) {
  if (SEVERITY_LABELS[severity]) {
    return `Meaning: ${SEVERITY_LABELS[severity]}.`;
  }
  return 'Meaning: Use a value from 1 to 5 where 5 is the highest risk severity.';
}

function priorityHint(priority) {
  if (!Number.isFinite(priority) || priority < 1) {
    return 'Meaning: Use a positive number. Lower numbers execute earlier than higher numbers.';
  }
  if (priority <= 25) {
    return 'Meaning: Very high execution priority (evaluated early).';
  }
  if (priority <= 100) {
    return 'Meaning: High execution priority (before most default rules).';
  }
  if (priority <= 500) {
    return 'Meaning: Normal execution priority.';
  }
  if (priority <= 1000) {
    return 'Meaning: Low execution priority (evaluated later).';
  }
  return 'Meaning: Very low execution priority (evaluated much later).';
}

function riskDeltaHint(riskDelta) {
  if (riskDelta <= -40) {
    return `Meaning: ${formatSigned(riskDelta)} is a critical trust-score penalty when this rule matches.`;
  }
  if (riskDelta <= -20) {
    return `Meaning: ${formatSigned(riskDelta)} is a high trust-score penalty when this rule matches.`;
  }
  if (riskDelta < 0) {
    return `Meaning: ${formatSigned(riskDelta)} is a low trust-score penalty when this rule matches.`;
  }
  if (riskDelta === 0) {
    return 'Meaning: 0 does not change trust score when this rule matches.';
  }
  if (riskDelta < 20) {
    return `Meaning: ${formatSigned(riskDelta)} gives a small trust-score boost when this rule matches.`;
  }
  return `Meaning: ${formatSigned(riskDelta)} gives a strong trust-score boost when this rule matches.`;
}

function setHint(id, text) {
  const el = document.getElementById(id);
  if (el) el.textContent = text;
}

function updateScorePreview() {
  const severity = toInt(document.getElementById('severity')?.value, 3);
  const priority = toInt(document.getElementById('priority')?.value, 100);
  const riskDelta = toInt(document.getElementById('risk_score_delta')?.value, 0);

  setHint('severity_hint', severityHint(severity));
  setHint('priority_hint', priorityHint(priority));
  setHint('risk_delta_hint', riskDeltaHint(riskDelta));
  setHint('score_preview_formula', `Formula: after = clamp(before ${riskDelta >= 0 ? '+' : '-'} ${Math.abs(riskDelta)}, 0..100).`);

  const samples = SCORE_PREVIEW_BASELINES
    .map((before) => {
      const after = clampScore(before + riskDelta);
      return `${before} -> ${after} (${decisionForScore(after)})`;
    })
    .join(' | ');
  setHint('score_preview_examples', `Examples using this delta: ${samples}.`);

  setHint(
    'score_preview_override_note',
    'Note: A matching Trust Score Policy can override this fallback delta with its own weighted score delta.'
  );
}

function generateRuleCode() {
  const now = new Date();
  const stamp = [
    now.getUTCFullYear(),
    String(now.getUTCMonth() + 1).padStart(2, '0'),
    String(now.getUTCDate()).padStart(2, '0'),
    String(now.getUTCHours()).padStart(2, '0'),
    String(now.getUTCMinutes()).padStart(2, '0'),
    String(now.getUTCSeconds()).padStart(2, '0')
  ].join('');

  const nonce = (window.crypto?.randomUUID?.() || Math.random().toString(16).slice(2))
    .replaceAll('-', '')
    .slice(0, 8)
    .toUpperCase();
  return `SR-${stamp}-${nonce}`;
}

function fillForm(row) {
  document.getElementById('edit_id').value = row?.id ?? '';
  const ruleCodeInput = document.getElementById('rule_code');
  if (ruleCodeInput) {
    ruleCodeInput.readOnly = true;
    if (row?.id) {
      ruleCodeInput.value = row?.rule_code ?? '';
    } else {
      ruleCodeInput.value = generateRuleCode();
    }
  }
  document.getElementById('rule_tag').value = row?.rule_tag ?? '';
  document.getElementById('os_type').value = row?.os_type ?? 'ANDROID';
  document.getElementById('device_type').value = row?.device_type ?? '';
  document.getElementById('status').value = row?.status ?? 'ACTIVE';
  document.getElementById('severity').value = row?.severity ?? 3;
  document.getElementById('priority').value = row?.priority ?? 100;
  document.getElementById('version').value = row?.version ?? 1;
  document.getElementById('match_mode').value = row?.match_mode ?? 'ALL';
  document.getElementById('compliance_action').value = row?.compliance_action ?? 'ALLOW';
  document.getElementById('risk_score_delta').value = row?.risk_score_delta ?? 0;
  document.getElementById('description').value = row?.description ?? '';
  updateScorePreview();
}

async function initFormLookups() {
  await Promise.all([
    populateLookupSelect('os_type', {
      lookupType: LOOKUP_TYPES.osType,
      fallbackOptions: OS_TYPE_OPTIONS
    }),
    populateLookupSelect('device_type', {
      lookupType: LOOKUP_TYPES.deviceType,
      fallbackOptions: DEVICE_TYPE_OPTIONS,
      emptyOption: { value: '', label: 'Any' }
    }),
    populateLookupSelect('status', {
      lookupType: LOOKUP_TYPES.recordStatus,
      fallbackOptions: STATUS_OPTIONS
    }),
    populateLookupSelect('match_mode', {
      lookupType: LOOKUP_TYPES.matchMode,
      fallbackOptions: MATCH_MODE_OPTIONS
    }),
    populateLookupSelect('compliance_action', {
      lookupType: LOOKUP_TYPES.complianceAction,
      fallbackOptions: COMPLIANCE_ACTION_OPTIONS
    })
  ]);
}

async function listAllSystemRules(requestHeaders) {
  const pageSize = 500;
  const pagesHardLimit = 20;
  const rows = [];
  let start = 0;

  for (let page = 0; page < pagesHardLimit; page += 1) {
    const response = await apiFetch(
      `/v1/ui/datatables/system-rules?draw=0&start=${start}&length=${pageSize}`,
      {
        headers: requestHeaders()
      }
    );
    const batch = Array.isArray(response?.data)
      ? response.data
      : (Array.isArray(response) ? response : []);
    if (!Array.isArray(batch) || !batch.length) {
      break;
    }
    rows.push(...batch);
    start += batch.length;

    const totalFiltered = Number.parseInt(
      String(
        response?.recordsFiltered
        ?? response?.records_filtered
        ?? response?.recordsTotal
        ?? response?.records_total
        ?? 0
      ),
      10
    );
    if (batch.length < pageSize) {
      break;
    }
    if (Number.isFinite(totalFiltered) && totalFiltered > 0 && start >= totalFiltered) {
      break;
    }
  }

  return rows;
}

function sortSystemRulesForPicker(rows) {
  return [...rows].sort((left, right) => {
    const leftCode = String(left?.rule_code || '').trim().toUpperCase();
    const rightCode = String(right?.rule_code || '').trim().toUpperCase();
    const byCode = leftCode.localeCompare(rightCode);
    if (byCode !== 0) {
      return byCode;
    }
    const leftId = Number.parseInt(String(left?.id ?? ''), 10);
    const rightId = Number.parseInt(String(right?.id ?? ''), 10);
    return leftId - rightId;
  });
}

function pickerOptionLabel(rule) {
  const id = Number.parseInt(String(rule?.id ?? ''), 10);
  const code = String(rule?.rule_code || '').trim();
  if (code) {
    return Number.isFinite(id) ? `${code} (#${id})` : code;
  }
  return Number.isFinite(id) ? `Rule #${id}` : 'Unnamed rule';
}

async function populateRuleCodePicker(requestHeaders, preferredRuleId = null) {
  const picker = document.getElementById('ruleCodePicker');
  if (!(picker instanceof HTMLSelectElement)) {
    return;
  }

  const currentValue = String(preferredRuleId ?? picker.value ?? '').trim();
  picker.disabled = true;
  picker.innerHTML = '';
  const loadingOption = document.createElement('option');
  loadingOption.value = '';
  loadingOption.textContent = 'Loading rule codes...';
  loadingOption.disabled = true;
  loadingOption.selected = true;
  picker.appendChild(loadingOption);

  try {
    const rules = await listAllSystemRules(requestHeaders);
    const uniqueRules = [];
    const seenIds = new Set();
    rules.forEach((rule) => {
      const id = Number.parseInt(String(rule?.id ?? ''), 10);
      if (!Number.isFinite(id) || seenIds.has(id)) {
        return;
      }
      seenIds.add(id);
      uniqueRules.push(rule);
    });
    const sortedRules = sortSystemRulesForPicker(uniqueRules);

    picker.innerHTML = '';
    const emptyOption = document.createElement('option');
    emptyOption.value = '';
    emptyOption.disabled = true;
    if (sortedRules.length) {
      emptyOption.textContent = 'Select system rule code';
    } else {
      emptyOption.textContent = 'No system rules available';
      emptyOption.selected = true;
    }
    picker.appendChild(emptyOption);

    sortedRules.forEach((rule) => {
      const id = Number.parseInt(String(rule?.id ?? ''), 10);
      if (!Number.isFinite(id)) {
        return;
      }
      const option = document.createElement('option');
      option.value = String(id);
      option.textContent = pickerOptionLabel(rule);
      picker.appendChild(option);
    });

    if (currentValue && Array.from(picker.options).some((opt) => opt.value === currentValue)) {
      picker.value = currentValue;
    } else if (sortedRules.length === 1) {
      picker.value = String(sortedRules[0].id);
    } else {
      picker.value = '';
      emptyOption.selected = true;
    }
  } catch (error) {
    picker.innerHTML = '';
    const failedOption = document.createElement('option');
    failedOption.value = '';
    failedOption.disabled = true;
    failedOption.selected = true;
    failedOption.textContent = 'Failed to load system rule codes';
    picker.appendChild(failedOption);
    window.mdmToast?.(`Unable to load rule codes: ${error?.message || 'Unknown error'}`);
  } finally {
    picker.disabled = false;
  }
}

document.addEventListener('DOMContentLoaded', async () => {
  await initFormLookups();
  const scope = await initPolicyScope();
  const requestHeaders = () => scope.getHeaders();
  const conditionsUrl = (id) => `/ui/policies/system-rules/${encodeURIComponent(id)}/conditions`;
  await populateRuleCodePicker(requestHeaders);

  document.getElementById('open-conditions-form')?.addEventListener('submit', (e) => {
    e.preventDefault();
    const picker = document.getElementById('ruleCodePicker');
    const id = picker instanceof HTMLSelectElement ? picker.value : '';
    if (!id) {
      window.mdmToast?.('Select a system rule code first.');
      return;
    }
    window.location.href = conditionsUrl(id);
  });

  const dt = window.mdmInitDataTable('#systemRulesTable', {
    ajax: {
      url: '/v1/ui/datatables/system-rules',
      dataSrc: 'data'
    },
    requestHeaders,
    columns: [
      { data: 'id' },
      { data: 'rule_code' },
      { data: 'os_type' },
      { data: 'device_type' },
      { data: 'status' },
      { data: 'priority' },
      {
        data: null,
        orderable: false,
        render: (_value, _type, row) => {
          const id = row?.id;
          const openConditionsButton = id
            ? '<button class="secondary" data-act="conditions" data-icon-action="open" data-icon-label="Open conditions">Conditions</button>'
            : '';
          return `${openConditionsButton}${renderPolicyActionButtons(scope, row)}`;
        }
      }
    ]
  });

  document.querySelector('#systemRulesTable').addEventListener('click', async (e) => {
    const btn = e.target.closest('button[data-act]');
    if (!btn) return;
    const tr = btn.closest('tr');
    const row = tr ? dt.row(tr).data() : null;
    const id = row?.id;
    if (!id) return;
    const act = btn.getAttribute('data-act');
    if (act === 'conditions') {
      const picker = document.getElementById('ruleCodePicker');
      if (picker instanceof HTMLSelectElement) {
        picker.value = String(id);
      }
      window.location.href = conditionsUrl(id);
      return;
    }
    if (act === 'edit') {
      if (!canWritePolicyRow(scope, row)) {
        window.mdmToast?.('This record is read-only in your scope. Clone it to your tenant to make changes.');
        return;
      }
      const fullRow = await apiFetch(`/v1/policies/system-rules/${encodeURIComponent(id)}`, {
        headers: requestHeaders()
      });
      fillForm(fullRow || row);
      window.mdmToast?.(`Editing #${id}`);
    }
    if (act === 'del') {
      if (!canWritePolicyRow(scope, row)) {
        window.mdmToast?.('This record is read-only in your scope. Clone it to your tenant to delete it.');
        return;
      }
      const confirmed = await confirmDanger(`Delete system rule #${id}?`);
      if (!confirmed) return;
      await apiFetch(`/v1/policies/system-rules/${encodeURIComponent(id)}`, {
        method: 'DELETE',
        headers: requestHeaders()
      });
      window.mdmToast?.('Deleted');
      dt.ajax.reload(null, false);
      await populateRuleCodePicker(requestHeaders);
      fillForm(null);
    }
    if (act === 'clone') {
      if (!shouldShowCloneToTenant(scope, row)) {
        return;
      }
      const result = await cloneSystemRuleToTenant(id, requestHeaders);
      if (!result) return;
      window.mdmToast?.(`Cloned to tenant (#${result.createdId}). Conditions copied: ${result.clonedConditions}.`);
      dt.ajax.reload(null, false);
      await populateRuleCodePicker(requestHeaders, result.createdId);
      fillForm(null);
    }
  });

  document.getElementById('systemRuleForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('edit_id').value;
    const payload = formJson();
    let selectedRuleId = null;
    if (!id) {
      const created = await apiFetch('/v1/policies/system-rules', {
        method: 'POST',
        body: JSON.stringify(payload),
        headers: requestHeaders()
      });
      selectedRuleId = created?.id ?? created?.rule_id ?? null;
      window.mdmToast?.('Created');
    } else {
      await apiFetch(`/v1/policies/system-rules/${encodeURIComponent(id)}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
        headers: requestHeaders()
      });
      selectedRuleId = id;
      window.mdmToast?.('Saved');
    }
    dt.ajax.reload(null, false);
    await populateRuleCodePicker(requestHeaders, selectedRuleId);
    fillForm(null);
  });

  document.getElementById('resetBtn').addEventListener('click', () => fillForm(null));
  ['severity', 'priority', 'risk_score_delta'].forEach((fieldId) => {
    const field = document.getElementById(fieldId);
    field?.addEventListener('input', updateScorePreview);
    field?.addEventListener('change', updateScorePreview);
  });
  fillForm(null);

  scope.onChange(async () => {
    dt.ajax.reload(null, false);
    await populateRuleCodePicker(requestHeaders);
    fillForm(null);
  });
});
