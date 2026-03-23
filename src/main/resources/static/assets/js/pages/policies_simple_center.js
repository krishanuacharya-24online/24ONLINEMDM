import { apiFetch } from '../api.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';
import { initPolicyScope } from '../policy-scope.js';

const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];
const OS_TYPE_OPTIONS = ['ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'];
const DEVICE_TYPE_OPTIONS = ['PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'IOT', 'SERVER'];
const REMEDIATION_TYPE_OPTIONS = ['USER_ACTION', 'AUTO_ACTION', 'NETWORK_RESTRICT', 'APP_REMOVAL', 'OS_UPDATE', 'POLICY_ACK'];
const COMPLIANCE_ACTION_OPTIONS = ['ALLOW', 'NOTIFY', 'QUARANTINE', 'BLOCK'];
const OPERATOR_OPTIONS = ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'REGEX', 'EXISTS', 'NOT_EXISTS'];
const DEVICE_FIELD_OPTIONS = [
  { value: 'root_detected', label: 'Root detected' },
  { value: 'running_on_emulator', label: 'Running on emulator' },
  { value: 'usb_debugging_status', label: 'USB debugging enabled' },
  { value: 'os_version', label: 'OS version' },
  { value: 'os_cycle', label: 'OS cycle or release channel' },
  { value: 'os_build_number', label: 'OS build number' },
  { value: 'api_level', label: 'API level' },
  { value: 'os_name', label: 'OS name' },
  { value: 'device_type', label: 'Device type' },
  { value: 'manufacturer', label: 'Manufacturer' },
  { value: 'time_zone', label: 'Time zone' },
  { value: 'kernel_version', label: 'Kernel version' },
  { value: 'installed_app_count', label: 'Installed app count' }
];
const VALUE_TYPE_OPTIONS = [
  { value: 'TEXT', label: 'Text' },
  { value: 'NUMBER', label: 'Number' },
  { value: 'BOOLEAN', label: 'Boolean' }
];
const SEVERITY_OPTIONS = [
  { value: '1', label: '1 - Low' },
  { value: '2', label: '2 - Guarded' },
  { value: '3', label: '3 - Medium' },
  { value: '4', label: '4 - High' },
  { value: '5', label: '5 - Critical' }
];
const DEVICE_TEMPLATES = {
  rooted: {
    name: 'Rooted Android device',
    description: 'Block Android devices that report root access.',
    os_type: 'ANDROID',
    device_type: '',
    severity: '5',
    field_name: 'root_detected',
    operator: 'EQ',
    value_type: 'BOOLEAN',
    value_boolean: 'true'
  },
  'ios-jailbreak': {
    name: 'Jailbroken iOS device',
    description: 'Block iOS devices that report jailbreak access.',
    os_type: 'IOS',
    device_type: '',
    severity: '5',
    field_name: 'root_detected',
    operator: 'EQ',
    value_type: 'BOOLEAN',
    value_boolean: 'true'
  },
  'usb-debugging': {
    name: 'USB debugging enabled',
    description: 'Quarantine devices that still have USB debugging enabled.',
    os_type: 'ANDROID',
    device_type: '',
    severity: '4',
    field_name: 'usb_debugging_status',
    operator: 'EQ',
    value_type: 'BOOLEAN',
    value_boolean: 'true'
  },
  emulator: {
    name: 'Running on emulator',
    description: 'Quarantine devices that are reporting as emulated or virtualized.',
    os_type: 'ANDROID',
    device_type: '',
    severity: '4',
    field_name: 'running_on_emulator',
    operator: 'EQ',
    value_type: 'BOOLEAN',
    value_boolean: 'true'
  },
  'windows-virtual': {
    name: 'Virtualized Windows device',
    description: 'Quarantine Windows devices that report emulator, simulator, or virtualization usage.',
    os_type: 'WINDOWS',
    device_type: '',
    severity: '4',
    field_name: 'running_on_emulator',
    operator: 'EQ',
    value_type: 'BOOLEAN',
    value_boolean: 'true'
  },
  'macos-virtual': {
    name: 'Virtualized macOS device',
    description: 'Quarantine macOS devices that report emulator, simulator, or virtualization usage.',
    os_type: 'MACOS',
    device_type: '',
    severity: '4',
    field_name: 'running_on_emulator',
    operator: 'EQ',
    value_type: 'BOOLEAN',
    value_boolean: 'true'
  },
  'linux-virtual': {
    name: 'Virtualized Linux device',
    description: 'Quarantine Linux devices that report emulator, simulator, or virtualization usage.',
    os_type: 'LINUX',
    device_type: '',
    severity: '4',
    field_name: 'running_on_emulator',
    operator: 'EQ',
    value_type: 'BOOLEAN',
    value_boolean: 'true'
  },
  'chromeos-virtual': {
    name: 'Virtualized ChromeOS device',
    description: 'Quarantine ChromeOS devices that report emulator, simulator, or virtualization usage.',
    os_type: 'CHROMEOS',
    device_type: '',
    severity: '4',
    field_name: 'running_on_emulator',
    operator: 'EQ',
    value_type: 'BOOLEAN',
    value_boolean: 'true'
  }
};
const APP_TEMPLATES = {
  'remote-admin-anydesk': {
    policy_tag: 'Block AnyDesk remote admin tool',
    app_os_type: 'WINDOWS',
    app_name: 'AnyDesk',
    package_id: 'AnyDeskSoftwareGmbH.AnyDesk',
    publisher: 'AnyDesk Software GmbH',
    min_allowed_version: '',
    severity: '4'
  },
  'remote-admin-teamviewer': {
    policy_tag: 'Block TeamViewer remote admin tool',
    app_os_type: 'WINDOWS',
    app_name: 'TeamViewer',
    package_id: '',
    publisher: 'TeamViewer Germany GmbH',
    min_allowed_version: '',
    severity: '4'
  }
};
const FIX_TEMPLATES = {
  'root-access': {
    title: 'Remove root access and rescan',
    description: 'Remove root tooling from the device, then run posture collection again.',
    remediation_type: 'USER_ACTION',
    os_type: 'ANDROID',
    device_type: '',
    priority: 10,
    steps: [
      'Remove root tooling from the device.',
      'Reboot the device to restore normal security state.',
      'Open the agent and run posture collection again.'
    ]
  },
  'jailbreak-access': {
    title: 'Remove jailbreak access and rescan',
    description: 'Remove jailbreak tooling from the device, then run posture collection again.',
    remediation_type: 'USER_ACTION',
    os_type: 'IOS',
    device_type: '',
    priority: 15,
    steps: [
      'Remove jailbreak tooling or restore the device to a supported state.',
      'Reboot the device so the integrity check can run cleanly.',
      'Open the agent and run posture collection again.'
    ]
  },
  'usb-debugging': {
    title: 'Turn off USB debugging',
    description: 'Disable USB debugging, then refresh the posture check.',
    remediation_type: 'USER_ACTION',
    os_type: 'ANDROID',
    device_type: '',
    priority: 20,
    steps: [
      'Open Developer Options on the device.',
      'Turn off USB debugging.',
      'Reconnect only after the policy check passes.'
    ]
  },
  'physical-device': {
    title: 'Move the workload to a supported physical device',
    description: 'Use a supported physical managed device instead of an emulator, simulator, or virtual machine, then collect posture again.',
    remediation_type: 'USER_ACTION',
    os_type: '',
    device_type: '',
    priority: 35,
    steps: [
      'Stop using the emulator, simulator, or virtual machine for the protected workflow.',
      'Move the user or workload to a supported physical managed device.',
      'Run posture collection again on the physical device.'
    ]
  },
  'remove-remote-admin': {
    title: 'Remove the blocked remote admin tool',
    description: 'Uninstall the blocked remote admin application and run posture collection again.',
    remediation_type: 'APP_REMOVAL',
    os_type: 'WINDOWS',
    device_type: '',
    priority: 40,
    steps: [
      'Uninstall the blocked remote admin application from the device.',
      'Confirm the application is no longer listed in installed programs.',
      'Open the agent and run posture collection again.'
    ]
  },
  'update-os': {
    title: 'Update the device to a supported OS release',
    description: 'Move the device to a currently supported operating-system release and scan again.',
    remediation_type: 'OS_UPDATE',
    os_type: '',
    device_type: '',
    priority: 50,
    steps: [
      'Review the supported OS baseline for the platform.',
      'Apply the latest supported OS release for the device.',
      'Run posture collection again after the update completes.'
    ]
  },
  'replace-os': {
    title: 'Move the device off the unsupported OS release',
    description: 'Upgrade or replace the device because the current operating-system release is no longer supportable.',
    remediation_type: 'OS_UPDATE',
    os_type: '',
    device_type: '',
    priority: 60,
    steps: [
      'Review the lifecycle baseline for the current operating-system release.',
      'Upgrade the device to a supported release or replace the device.',
      'Run posture collection again after the change.'
    ]
  }
};
const STARTER_PACK_FIX_TITLES = [
  'Remove root access and rescan',
  'Remove jailbreak access and rescan',
  'Turn off USB debugging',
  'Stop using an emulated device',
  'Move the workload to a supported physical device',
  'Remove the blocked remote admin tool',
  'Update the device to a supported OS release',
  'Move the device off the unsupported OS release'
];
const STARTER_PACK_DEVICE_POLICY_NAMES = [
  'Rooted Android device',
  'Jailbroken iOS device',
  'USB debugging enabled',
  'Running on emulator',
  'Virtualized Windows device',
  'Virtualized macOS device',
  'Virtualized Linux device',
  'Virtualized ChromeOS device',
  'Virtualized FreeBSD device',
  'Virtualized OpenBSD device'
];
const STARTER_PACK_APP_POLICY_NAMES = [
  'Block AnyDesk remote admin tool',
  'Block TeamViewer remote admin tool'
];
const STARTER_PACK_TRUST_LEVELS = [
  { score_min: 80, score_max: 100, decision_action: 'ALLOW' },
  { score_min: 60, score_max: 79, decision_action: 'NOTIFY' },
  { score_min: 40, score_max: 59, decision_action: 'QUARANTINE' },
  { score_min: 0, score_max: 39, decision_action: 'BLOCK' }
];
const FIELD_LABELS = new Map(DEVICE_FIELD_OPTIONS.map((option) => [option.value, option.label]));
const SEVERITY_LABELS = new Map(SEVERITY_OPTIONS.map((option) => [option.value, option.label.replace(/^\d+\s*-\s*/, '')]));

const state = {
  scope: null,
  devicePolicies: [],
  appPolicies: [],
  trustLevels: [],
  fixes: [],
  fixOptions: [],
  simulatedApps: [],
  simulationResult: null
};

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function text(value, fallback = '-') {
  const normalized = String(value ?? '').trim();
  return normalized || fallback;
}

function setError(id, message) {
  const node = document.getElementById(id);
  if (!node) return;
  node.hidden = !message;
  node.textContent = message || '';
}

function clearError(id) {
  setError(id, '');
}

function setStarterPackMessage(message, isError = false) {
  const node = document.getElementById('starterPackMessage');
  if (!node) return;
  node.hidden = !message;
  node.textContent = message || '';
  node.classList.toggle('form-message--error', Boolean(message) && isError);
}

function normalizeLookupKey(value) {
  const normalized = String(value ?? '').trim();
  return normalized ? normalized.toLowerCase() : '';
}

function starterTrustLevelSignature(row) {
  return [
    Number.parseInt(String(row?.score_min ?? '-1'), 10),
    Number.parseInt(String(row?.score_max ?? '-1'), 10),
    String(row?.decision_action ?? '').trim().toUpperCase()
  ].join('|');
}

function hasStarterPackInstalled() {
  const fixTitles = new Set((state.fixes || []).map((row) => normalizeLookupKey(row?.title)));
  const deviceNames = new Set((state.devicePolicies || []).map((row) => normalizeLookupKey(row?.name)));
  const appPolicyNames = new Set((state.appPolicies || []).map((row) => normalizeLookupKey(row?.policy_tag)));
  const trustSignatures = new Set((state.trustLevels || []).map((row) => starterTrustLevelSignature(row)));

  const hasAllFixes = STARTER_PACK_FIX_TITLES.every((title) => fixTitles.has(normalizeLookupKey(title)));
  const hasAllDeviceChecks = STARTER_PACK_DEVICE_POLICY_NAMES.every((name) => deviceNames.has(normalizeLookupKey(name)));
  const hasAllAppRules = STARTER_PACK_APP_POLICY_NAMES.every((name) => appPolicyNames.has(normalizeLookupKey(name)));
  const hasAllTrustLevels = STARTER_PACK_TRUST_LEVELS.every((row) => trustSignatures.has(starterTrustLevelSignature(row)));

  return hasAllFixes && hasAllDeviceChecks && hasAllAppRules && hasAllTrustLevels;
}

function updateStarterPackVisibility() {
  const action = document.getElementById('starterPackAction');
  const note = document.getElementById('starterPackInstalledNote');
  const installed = hasStarterPackInstalled();

  if (action) {
    action.hidden = installed;
  }
  if (note) {
    note.hidden = !installed;
  }
}

function advancedModeAction(url) {
  if (state.scope?.role === 'PRODUCT_ADMIN') {
    return `<a class="link-btn" href="${url}">Advanced</a>`;
  }
  return '<span class="muted">Product admin only</span>';
}

function jumpToSection(id) {
  const node = document.getElementById(id);
  if (!node) return;
  node.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function confirmAction({ title, message, confirmLabel = 'Confirm', cancelLabel = 'Cancel', danger = false }) {
  if (typeof window.mdmConfirm === 'function') {
    return window.mdmConfirm({
      title,
      message,
      confirmLabel,
      cancelLabel,
      danger
    });
  }
  return window.confirm(message);
}

async function confirmDanger(message) {
  return confirmAction({
    title: 'Confirm delete',
    message,
    confirmLabel: 'Delete',
    cancelLabel: 'Cancel',
    danger: true
  });
}

function fillSelect(selectId, options, emptyLabel = '') {
  const select = document.getElementById(selectId);
  if (!select) return;
  const currentValue = String(select.value || '');
  select.innerHTML = '';
  if (emptyLabel !== null) {
    const empty = document.createElement('option');
    empty.value = '';
    empty.textContent = emptyLabel;
    select.appendChild(empty);
  }
  options.forEach((option) => {
    const node = document.createElement('option');
    node.value = String(option.value);
    node.textContent = String(option.label);
    select.appendChild(node);
  });
  if (Array.from(select.options).some((option) => option.value === currentValue)) {
    select.value = currentValue;
  }
}

function valueTypeVisibility() {
  const operator = String(document.getElementById('device_operator')?.value || '').trim().toUpperCase();
  const valueType = String(document.getElementById('device_value_type')?.value || 'TEXT').trim().toUpperCase();
  const needsValue = operator !== 'EXISTS' && operator !== 'NOT_EXISTS';
  document.getElementById('device_value_type')?.toggleAttribute('disabled', !needsValue);
  document.querySelectorAll('[data-device-value-row]').forEach((node) => {
    const kind = node.getAttribute('data-device-value-row');
    node.hidden = !needsValue || kind !== valueType.toLowerCase();
  });
}

function fixStepsFromJson(instructionJson) {
  const raw = String(instructionJson || '').trim();
  if (!raw) return '';
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed?.steps)) return parsed.steps.join('\n');
    if (Array.isArray(parsed)) return parsed.join('\n');
    if (Array.isArray(parsed?.instructions)) return parsed.instructions.join('\n');
  } catch {
  }
  return raw;
}

function buildInstructionJson(title, description, stepsText) {
  const steps = String(stepsText || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  return JSON.stringify({
    version: 1,
    title: String(title || '').trim(),
    summary: String(description || '').trim(),
    steps
  });
}

function scoreDeltaLabel(value) {
  if (value == null || value === '') return '-';
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? `+${n}` : String(value);
}

function deviceCheckLabel(row) {
  const operator = text(row.operator, '');
  const valueType = text(row.value_type, '');
  const value = valueType === 'BOOLEAN'
    ? String(row.value_boolean)
    : valueType === 'NUMBER'
      ? text(row.value_numeric)
      : text(row.value_text);
  if (operator === 'EXISTS' || operator === 'NOT_EXISTS') {
    return `${text(row.field_name)} ${operator}`;
  }
  return `${text(row.field_name)} ${operator} ${value}`;
}

function deviceDuplicateMap(rows) {
  const counts = new Map();
  (rows || []).forEach((row) => {
    const signature = [
      text(row.os_type, ''),
      text(row.device_type, ''),
      text(row.field_name, ''),
      text(row.operator, ''),
      text(row.value_type, ''),
      text(row.value_text, ''),
      text(row.value_numeric, ''),
      text(row.value_boolean, '')
    ].join('|').toLowerCase();
    counts.set(signature, (counts.get(signature) || 0) + 1);
  });
  return counts;
}

function appDuplicateMap(rows) {
  const counts = new Map();
  (rows || []).forEach((row) => {
    const signature = [
      text(row.app_os_type, ''),
      text(row.package_id, ''),
      text(row.app_name, ''),
      text(row.min_allowed_version, '')
    ].join('|').toLowerCase();
    counts.set(signature, (counts.get(signature) || 0) + 1);
  });
  return counts;
}

function deviceDuplicateWarning(row, counts) {
  const signature = [
    text(row.os_type, ''),
    text(row.device_type, ''),
    text(row.field_name, ''),
    text(row.operator, ''),
    text(row.value_type, ''),
    text(row.value_text, ''),
    text(row.value_numeric, ''),
    text(row.value_boolean, '')
  ].join('|').toLowerCase();
  return (counts.get(signature) || 0) > 1 ? 'Potential duplicate check in this scope.' : '';
}

function appDuplicateWarning(row, counts) {
  const signature = [
    text(row.app_os_type, ''),
    text(row.package_id, ''),
    text(row.app_name, ''),
    text(row.min_allowed_version, '')
  ].join('|').toLowerCase();
  return (counts.get(signature) || 0) > 1 ? 'Potential duplicate app rule in this scope.' : '';
}

function selectedText(selectId, fallback = '') {
  const select = document.getElementById(selectId);
  if (!select) return fallback;
  const option = select.options[select.selectedIndex];
  return option?.textContent?.trim() || fallback;
}

function setPreview(id, message) {
  const node = document.getElementById(id);
  if (!node) return;
  node.textContent = message || '';
}

function currentDeviceValueLabel() {
  const operator = String(document.getElementById('device_operator')?.value || '').trim().toUpperCase();
  if (operator === 'EXISTS' || operator === 'NOT_EXISTS') {
    return '';
  }
  const valueType = String(document.getElementById('device_value_type')?.value || 'TEXT').trim().toUpperCase();
  if (valueType === 'BOOLEAN') {
    const raw = document.getElementById('device_value_boolean')?.value;
    return raw === 'true' ? 'true' : raw === 'false' ? 'false' : '?';
  }
  if (valueType === 'NUMBER') {
    return document.getElementById('device_value_numeric')?.value || '?';
  }
  return document.getElementById('device_value_text')?.value || '?';
}

function updateDevicePreview() {
  const name = text(document.getElementById('device_policy_name')?.value, 'Unnamed device check');
  const osType = text(document.getElementById('device_os_type')?.value, 'ANY');
  const deviceType = text(document.getElementById('device_device_type')?.value, 'any device');
  const fieldName = FIELD_LABELS.get(document.getElementById('device_field_name')?.value) || 'selected field';
  const operator = text(document.getElementById('device_operator')?.value, 'EQ');
  const severity = SEVERITY_LABELS.get(document.getElementById('device_severity')?.value) || 'Medium';
  const fix = selectedText('device_remediation_rule_id', 'no linked fix');
  const valueSuffix = ['EXISTS', 'NOT_EXISTS'].includes(operator) ? '' : ` ${currentDeviceValueLabel()}`;
  setPreview(
    'device_policy_preview',
    `Preview: "${name}" checks ${fieldName} ${operator}${valueSuffix} for ${osType} ${deviceType}. Severity is ${severity}; linked fix is ${fix}.`
  );
}

function updateAppPreview() {
  const name = text(document.getElementById('app_policy_tag')?.value, 'Unnamed app rule');
  const appName = text(document.getElementById('app_name')?.value, 'selected app');
  const osType = text(document.getElementById('app_os_type')?.value, 'ANY');
  const minVersion = text(document.getElementById('app_min_allowed_version')?.value, 'Any version');
  const severity = SEVERITY_LABELS.get(document.getElementById('app_severity')?.value) || 'Medium';
  const fix = selectedText('app_remediation_rule_id', 'no linked fix');
  setPreview(
    'app_policy_preview',
    `Preview: "${name}" treats ${appName} on ${osType} as a ${severity.toLowerCase()} issue. Minimum allowed version is ${minVersion}; linked fix is ${fix}.`
  );
}

function updateTrustPreview() {
  const name = text(document.getElementById('trust_policy_name')?.value, 'Unnamed trust level');
  const scoreMin = text(document.getElementById('trust_score_min')?.value, '?');
  const scoreMax = text(document.getElementById('trust_score_max')?.value, '?');
  const action = text(document.getElementById('trust_decision_action')?.value, 'ALLOW');
  const remediation = document.getElementById('trust_remediation_required')?.checked ? 'required' : 'optional';
  setPreview(
    'trust_level_preview',
    `Preview: "${name}" applies when trust score is between ${scoreMin} and ${scoreMax}. Final action is ${action}; remediation is ${remediation}.`
  );
}

function updateFixPreview() {
  const title = text(document.getElementById('fix_title')?.value, 'Untitled fix');
  const type = text(document.getElementById('fix_remediation_type')?.value, 'USER_ACTION');
  const osType = text(document.getElementById('fix_os_type')?.value, 'any OS');
  const deviceType = text(document.getElementById('fix_device_type')?.value, 'any device');
  const steps = String(document.getElementById('fix_steps')?.value || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .length;
  setPreview(
    'fix_library_preview',
    `Preview: "${title}" is a ${type} fix for ${osType} ${deviceType}. It currently has ${steps} step${steps === 1 ? '' : 's'}.`
  );
}

function updateAllPreviews() {
  updateDevicePreview();
  updateAppPreview();
  updateTrustPreview();
  updateFixPreview();
}

function renderDevicePolicies() {
  const tbody = document.querySelector('#simpleDevicePoliciesTable tbody');
  if (!tbody) return;
  const duplicates = deviceDuplicateMap(state.devicePolicies);
  tbody.innerHTML = state.devicePolicies.map((row) => `
    <tr>
      <td>${escapeHtml(text(row.name))}${deviceDuplicateWarning(row, duplicates) ? `<div class="muted">${escapeHtml(deviceDuplicateWarning(row, duplicates))}</div>` : ''}</td>
      <td>${escapeHtml([text(row.os_type, ''), text(row.device_type, '')].filter(Boolean).join(' / ') || 'Any')}</td>
      <td>${escapeHtml(deviceCheckLabel(row))}</td>
      <td>${escapeHtml(`${text(row.severity)} (${scoreDeltaLabel(row.score_delta)})`)}</td>
      <td>${escapeHtml(text(row.remediation_title))}</td>
      <td>${escapeHtml(text(row.status))}</td>
      <td>${row.complex ? `<span class="muted">Advanced only${row.complexity_reason ? `: ${escapeHtml(row.complexity_reason)}` : ''}</span>` : 'Simple'}</td>
      <td>
        ${row.complex ? advancedModeAction('/ui/policies/system-rules') : `<button type="button" class="secondary" data-device-edit="${row.id}">Edit</button>`}
        <button type="button" class="secondary" data-device-delete="${row.id}">Delete</button>
      </td>
    </tr>
  `).join('') || '<tr><td colspan="8" class="muted">No device checks found.</td></tr>';
}

function renderAppPolicies() {
  const tbody = document.querySelector('#simpleAppPoliciesTable tbody');
  if (!tbody) return;
  const duplicates = appDuplicateMap(state.appPolicies);
  tbody.innerHTML = state.appPolicies.map((row) => `
    <tr>
      <td>${escapeHtml(text(row.policy_tag))}${appDuplicateWarning(row, duplicates) ? `<div class="muted">${escapeHtml(appDuplicateWarning(row, duplicates))}</div>` : ''}</td>
      <td>${escapeHtml(text(row.app_name))}${row.package_id ? `<div class="muted">${escapeHtml(row.package_id)}</div>` : ''}</td>
      <td>${escapeHtml(text(row.app_os_type))}</td>
      <td>${escapeHtml(text(row.min_allowed_version, 'Any version'))}</td>
      <td>${escapeHtml(`${text(row.severity)} (${scoreDeltaLabel(row.score_delta)})`)}</td>
      <td>${escapeHtml(text(row.remediation_title))}</td>
      <td>${escapeHtml(text(row.status))}</td>
      <td>${row.complex ? `<span class="muted">Advanced only${row.complexity_reason ? `: ${escapeHtml(row.complexity_reason)}` : ''}</span>` : 'Simple'}</td>
      <td>
        ${row.complex ? advancedModeAction('/ui/policies/reject-apps') : `<button type="button" class="secondary" data-app-edit="${row.id}">Edit</button>`}
        <button type="button" class="secondary" data-app-delete="${row.id}">Delete</button>
      </td>
    </tr>
  `).join('') || '<tr><td colspan="9" class="muted">No app rules found.</td></tr>';
}

function renderTrustLevels() {
  const tbody = document.querySelector('#simpleTrustLevelsTable tbody');
  if (!tbody) return;
  tbody.innerHTML = state.trustLevels
    .slice()
    .sort((a, b) => Number(a.score_min ?? 0) - Number(b.score_min ?? 0))
    .map((row) => `
      <tr>
        <td>${escapeHtml(text(row.policy_name))}</td>
        <td>${escapeHtml(`${text(row.score_min)} to ${text(row.score_max)}`)}</td>
        <td>${escapeHtml(text(row.decision_action))}</td>
        <td>${row.remediation_required ? 'Required' : 'Optional'}</td>
        <td>${escapeHtml(text(row.status))}</td>
        <td>
          <button type="button" class="secondary" data-trust-edit="${row.id}">Edit</button>
          <button type="button" class="secondary" data-trust-delete="${row.id}">Delete</button>
        </td>
      </tr>
    `).join('') || '<tr><td colspan="6" class="muted">No trust levels found.</td></tr>';
}

function renderFixLibrary() {
  const tbody = document.querySelector('#simpleFixLibraryTable tbody');
  if (!tbody) return;
  tbody.innerHTML = state.fixes.map((row) => `
    <tr>
      <td>${escapeHtml(text(row.title))}</td>
      <td>${escapeHtml(text(row.remediation_type))}</td>
      <td>${escapeHtml([text(row.os_type, ''), text(row.device_type, '')].filter(Boolean).join(' / ') || 'Any')}</td>
      <td>${escapeHtml(text(row.status))}</td>
      <td>
        <button type="button" class="secondary" data-fix-edit="${row.id}">Edit</button>
        <button type="button" class="secondary" data-fix-delete="${row.id}">Delete</button>
      </td>
    </tr>
  `).join('') || '<tr><td colspan="5" class="muted">No fix instructions found.</td></tr>';
  populateFixSelectOptions();
}

function populateFixSelectOptions() {
  const options = state.fixOptions.map((row) => ({
    value: String(row.id),
    label: `${text(row.title)} (${text(row.remediation_type)})`
  }));
  fillSelect('device_remediation_rule_id', options, 'No fix instruction');
  fillSelect('app_remediation_rule_id', options, 'No fix instruction');
  updateDevicePreview();
  updateAppPreview();
}

function resetDeviceForm() {
  document.getElementById('simpleDevicePolicyForm')?.reset();
  document.getElementById('device_policy_edit_id').value = '';
  document.getElementById('device_status').value = 'ACTIVE';
  document.getElementById('device_severity').value = '3';
  document.getElementById('device_value_type').value = 'TEXT';
  valueTypeVisibility();
  clearError('devicePolicyFormError');
  updateDevicePreview();
}

function resetAppForm() {
  document.getElementById('simpleAppPolicyForm')?.reset();
  document.getElementById('app_policy_edit_id').value = '';
  document.getElementById('app_status').value = 'ACTIVE';
  document.getElementById('app_severity').value = '3';
  clearError('appPolicyFormError');
  updateAppPreview();
}

function resetTrustForm() {
  document.getElementById('simpleTrustLevelForm')?.reset();
  document.getElementById('trust_policy_edit_id').value = '';
  document.getElementById('trust_status').value = 'ACTIVE';
  clearError('trustLevelFormError');
  updateTrustPreview();
}

function resetFixForm() {
  document.getElementById('simpleFixLibraryForm')?.reset();
  document.getElementById('fix_edit_id').value = '';
  document.getElementById('fix_existing_code').value = '';
  document.getElementById('fix_priority').value = '100';
  document.getElementById('fix_status').value = 'ACTIVE';
  clearError('fixLibraryFormError');
  updateFixPreview();
}

function fillDeviceForm(row) {
  document.getElementById('device_policy_edit_id').value = row.id;
  document.getElementById('device_policy_name').value = row.name || '';
  document.getElementById('device_policy_description').value = row.description || '';
  document.getElementById('device_os_type').value = row.os_type || 'ANDROID';
  document.getElementById('device_device_type').value = row.device_type || '';
  document.getElementById('device_severity').value = String(row.severity || '3');
  document.getElementById('device_remediation_rule_id').value = row.remediation_rule_id == null ? '' : String(row.remediation_rule_id);
  document.getElementById('device_status').value = row.status || 'ACTIVE';
  document.getElementById('device_field_name').value = row.field_name || '';
  document.getElementById('device_operator').value = row.operator || 'EQ';
  document.getElementById('device_value_type').value = row.value_type || 'TEXT';
  document.getElementById('device_value_text').value = row.value_text || '';
  document.getElementById('device_value_numeric').value = row.value_numeric ?? '';
  document.getElementById('device_value_boolean').value = row.value_boolean == null ? '' : String(row.value_boolean);
  valueTypeVisibility();
  updateDevicePreview();
}

function fillAppForm(row) {
  document.getElementById('app_policy_edit_id').value = row.id;
  document.getElementById('app_policy_tag').value = row.policy_tag || '';
  document.getElementById('app_os_type').value = row.app_os_type || 'ANDROID';
  document.getElementById('app_name').value = row.app_name || '';
  document.getElementById('app_package_id').value = row.package_id || '';
  document.getElementById('app_publisher').value = row.publisher || '';
  document.getElementById('app_min_allowed_version').value = row.min_allowed_version || '';
  document.getElementById('app_severity').value = String(row.severity || '3');
  document.getElementById('app_remediation_rule_id').value = row.remediation_rule_id == null ? '' : String(row.remediation_rule_id);
  document.getElementById('app_status').value = row.status || 'ACTIVE';
  updateAppPreview();
}

function fillTrustForm(row) {
  document.getElementById('trust_policy_edit_id').value = row.id;
  document.getElementById('trust_policy_name').value = row.policy_name || '';
  document.getElementById('trust_score_min').value = row.score_min ?? '';
  document.getElementById('trust_score_max').value = row.score_max ?? '';
  document.getElementById('trust_decision_action').value = row.decision_action || 'ALLOW';
  document.getElementById('trust_response_message').value = row.response_message || '';
  document.getElementById('trust_status').value = row.status || 'ACTIVE';
  document.getElementById('trust_remediation_required').checked = Boolean(row.remediation_required);
  updateTrustPreview();
}

function fillFixForm(row) {
  document.getElementById('fix_edit_id').value = row.id;
  document.getElementById('fix_existing_code').value = row.remediation_code || '';
  document.getElementById('fix_title').value = row.title || '';
  document.getElementById('fix_description').value = row.description || '';
  document.getElementById('fix_remediation_type').value = row.remediation_type || 'USER_ACTION';
  document.getElementById('fix_os_type').value = row.os_type || '';
  document.getElementById('fix_device_type').value = row.device_type || '';
  document.getElementById('fix_steps').value = fixStepsFromJson(row.instruction_json ?? row.instructionJson);
  document.getElementById('fix_priority').value = row.priority ?? 100;
  document.getElementById('fix_status').value = row.status || 'ACTIVE';
  updateFixPreview();
}

function applyDeviceTemplate(templateKey) {
  const template = DEVICE_TEMPLATES[templateKey];
  if (!template) return;
  resetDeviceForm();
  document.getElementById('device_policy_name').value = template.name;
  document.getElementById('device_policy_description').value = template.description;
  document.getElementById('device_os_type').value = template.os_type;
  document.getElementById('device_device_type').value = template.device_type;
  document.getElementById('device_severity').value = template.severity;
  document.getElementById('device_field_name').value = template.field_name;
  document.getElementById('device_operator').value = template.operator;
  document.getElementById('device_value_type').value = template.value_type;
  document.getElementById('device_value_text').value = template.value_text || '';
  document.getElementById('device_value_numeric').value = template.value_numeric ?? '';
  document.getElementById('device_value_boolean').value = template.value_boolean || '';
  valueTypeVisibility();
  updateDevicePreview();
}

function applyAppTemplate(templateKey) {
  const template = APP_TEMPLATES[templateKey];
  if (!template) return;
  resetAppForm();
  document.getElementById('app_policy_tag').value = template.policy_tag;
  document.getElementById('app_os_type').value = template.app_os_type;
  document.getElementById('app_name').value = template.app_name;
  document.getElementById('app_package_id').value = template.package_id || '';
  document.getElementById('app_publisher').value = template.publisher || '';
  document.getElementById('app_min_allowed_version').value = template.min_allowed_version || '';
  document.getElementById('app_severity').value = template.severity;
  updateAppPreview();
}

function applyFixTemplate(templateKey) {
  const template = FIX_TEMPLATES[templateKey];
  if (!template) return;
  resetFixForm();
  document.getElementById('fix_title').value = template.title;
  document.getElementById('fix_description').value = template.description;
  document.getElementById('fix_remediation_type').value = template.remediation_type;
  document.getElementById('fix_os_type').value = template.os_type || '';
  document.getElementById('fix_device_type').value = template.device_type || '';
  document.getElementById('fix_steps').value = Array.isArray(template.steps) ? template.steps.join('\n') : '';
  document.getElementById('fix_priority').value = template.priority;
  updateFixPreview();
}

function summarizeStarterPack(result) {
  if (!result) {
    return 'Baseline posture pack installed.';
  }
  const scope = text(result.scope, 'current scope');
  return `Baseline pack applied to ${scope}: ${Number(result.created_fixes ?? 0)} fixes, ${Number(result.created_device_checks ?? 0)} device checks, ${Number(result.created_app_rules ?? 0)} app rules, ${Number(result.created_trust_levels ?? 0)} trust levels created.`;
}

function createSimulatedApp() {
  return {
    app_name: '',
    package_id: '',
    app_version: '',
    publisher: '',
    app_os_type: 'ANDROID'
  };
}

function renderSimulatedApps() {
  const tbody = document.querySelector('#simulatedAppsTable tbody');
  if (!tbody) return;
  tbody.innerHTML = state.simulatedApps.map((row, index) => `
    <tr>
      <td><input type="text" data-sim-app-field="app_name" data-sim-app-index="${index}" value="${escapeHtml(text(row.app_name, ''))}"/></td>
      <td><input type="text" data-sim-app-field="package_id" data-sim-app-index="${index}" value="${escapeHtml(text(row.package_id, ''))}"/></td>
      <td><input type="text" data-sim-app-field="app_version" data-sim-app-index="${index}" value="${escapeHtml(text(row.app_version, ''))}"/></td>
      <td><input type="text" data-sim-app-field="publisher" data-sim-app-index="${index}" value="${escapeHtml(text(row.publisher, ''))}"/></td>
      <td>
        <select data-sim-app-field="app_os_type" data-sim-app-index="${index}">
          ${OS_TYPE_OPTIONS.map((option) => `<option value="${escapeHtml(option)}" ${String(row.app_os_type || 'ANDROID') === option ? 'selected' : ''}>${escapeHtml(option)}</option>`).join('')}
        </select>
      </td>
      <td><button type="button" class="secondary" data-sim-app-delete="${index}">Remove</button></td>
    </tr>
  `).join('') || '<tr><td colspan="6" class="muted">No sample apps added.</td></tr>';
}

function resetSimulatorForm() {
  document.getElementById('simplePolicySimulatorForm')?.reset();
  document.getElementById('sim_current_score').value = '100';
  document.getElementById('sim_os_type').value = 'ANDROID';
  document.getElementById('sim_device_type').value = 'PHONE';
  state.simulatedApps = [];
  state.simulationResult = null;
  renderSimulatedApps();
  renderSimulationResult();
  clearError('policySimulatorError');
}

function collectSimulatorPayload() {
  return {
    current_score: Number.parseInt(document.getElementById('sim_current_score').value || '100', 10),
    os_type: document.getElementById('sim_os_type').value,
    os_name: document.getElementById('sim_os_name').value || null,
    os_version: document.getElementById('sim_os_version').value || null,
    os_cycle: document.getElementById('sim_os_cycle').value || null,
    device_type: document.getElementById('sim_device_type').value || null,
    api_level: document.getElementById('sim_api_level').value ? Number.parseInt(document.getElementById('sim_api_level').value, 10) : null,
    manufacturer: document.getElementById('sim_manufacturer').value || null,
    root_detected: document.getElementById('sim_root_detected').checked,
    running_on_emulator: document.getElementById('sim_running_on_emulator').checked,
    usb_debugging_status: document.getElementById('sim_usb_debugging_status').checked,
    installed_apps: state.simulatedApps
      .map((row) => ({
        app_name: String(row.app_name || '').trim() || null,
        package_id: String(row.package_id || '').trim() || null,
        app_version: String(row.app_version || '').trim() || null,
        publisher: String(row.publisher || '').trim() || null,
        app_os_type: String(row.app_os_type || '').trim() || null
      }))
      .filter((row) => row.app_name || row.package_id)
  };
}

function renderSimulationResult() {
  const empty = document.getElementById('policySimulatorEmpty');
  const results = document.getElementById('policySimulatorResults');
  const summary = document.getElementById('policySimulatorSummary');
  const tbody = document.querySelector('#policySimulatorFindingsTable tbody');
  if (!summary || !tbody || !empty || !results) return;

  if (!state.simulationResult) {
    empty.hidden = false;
    results.hidden = true;
    summary.textContent = '';
    tbody.innerHTML = '';
    return;
  }

  const result = state.simulationResult;
  empty.hidden = true;
  results.hidden = false;
  summary.textContent = `Before: ${text(result.score_before)} | After: ${text(result.score_after)} | Delta: ${scoreDeltaLabel(result.score_delta_total)} | Decision: ${text(result.decision_action)} | Lifecycle: ${text(result.lifecycle_state)} | Reason: ${text(result.decision_reason)}`;
  tbody.innerHTML = (result.findings || []).map((row) => `
    <tr>
      <td>${escapeHtml(text(row.category))}</td>
      <td>${escapeHtml(text(row.title))}</td>
      <td>${escapeHtml(text(row.detail))}</td>
      <td>${escapeHtml(text(row.severity))}</td>
      <td>${escapeHtml(text(row.action))}</td>
      <td>${escapeHtml(scoreDeltaLabel(row.score_delta))}</td>
    </tr>
  `).join('') || '<tr><td colspan="6" class="muted">No findings matched.</td></tr>';
}

async function loadAll() {
  const headers = state.scope.getHeaders();
  state.devicePolicies = await apiFetch('/v1/policies/simple/device-checks', { headers }).catch(() => []);
  state.appPolicies = await apiFetch('/v1/policies/simple/app-rules', { headers }).catch(() => []);
  state.trustLevels = await apiFetch('/v1/policies/simple/trust-levels', { headers }).catch(() => []);
  state.fixes = await apiFetch('/v1/policies/simple/fix-library', { headers }).catch(() => []);
  state.fixOptions = await apiFetch('/v1/policies/simple/fix-options', { headers }).catch(() => []);
  updateStarterPackVisibility();
  renderDevicePolicies();
  renderAppPolicies();
  renderTrustLevels();
  renderFixLibrary();
  updateAllPreviews();
}

document.addEventListener('DOMContentLoaded', async () => {
  state.scope = await initPolicyScope();

  fillSelect('device_field_name', DEVICE_FIELD_OPTIONS, 'Select field');
  fillSelect('device_value_type', VALUE_TYPE_OPTIONS, null);
  fillSelect('device_severity', SEVERITY_OPTIONS, null);
  fillSelect('app_severity', SEVERITY_OPTIONS, null);

  await Promise.all([
    populateLookupSelect('device_os_type', { lookupType: LOOKUP_TYPES.osType, fallbackOptions: OS_TYPE_OPTIONS }),
    populateLookupSelect('device_device_type', { lookupType: LOOKUP_TYPES.deviceType, fallbackOptions: DEVICE_TYPE_OPTIONS, emptyOption: { value: '', label: 'Any device' } }),
    populateLookupSelect('device_status', { lookupType: LOOKUP_TYPES.recordStatus, fallbackOptions: STATUS_OPTIONS }),
    populateLookupSelect('device_operator', { lookupType: LOOKUP_TYPES.ruleConditionOperator, fallbackOptions: OPERATOR_OPTIONS }),
    populateLookupSelect('app_os_type', { lookupType: LOOKUP_TYPES.osType, fallbackOptions: OS_TYPE_OPTIONS }),
    populateLookupSelect('app_status', { lookupType: LOOKUP_TYPES.recordStatus, fallbackOptions: STATUS_OPTIONS }),
    populateLookupSelect('trust_decision_action', { lookupType: LOOKUP_TYPES.complianceAction, fallbackOptions: COMPLIANCE_ACTION_OPTIONS }),
    populateLookupSelect('trust_status', { lookupType: LOOKUP_TYPES.recordStatus, fallbackOptions: STATUS_OPTIONS }),
    populateLookupSelect('fix_remediation_type', { lookupType: LOOKUP_TYPES.remediationType, fallbackOptions: REMEDIATION_TYPE_OPTIONS }),
    populateLookupSelect('fix_os_type', { lookupType: LOOKUP_TYPES.osType, fallbackOptions: OS_TYPE_OPTIONS, emptyOption: { value: '', label: 'Any OS' } }),
    populateLookupSelect('fix_device_type', { lookupType: LOOKUP_TYPES.deviceType, fallbackOptions: DEVICE_TYPE_OPTIONS, emptyOption: { value: '', label: 'Any device' } }),
    populateLookupSelect('fix_status', { lookupType: LOOKUP_TYPES.recordStatus, fallbackOptions: STATUS_OPTIONS }),
    populateLookupSelect('sim_os_type', { lookupType: LOOKUP_TYPES.osType, fallbackOptions: OS_TYPE_OPTIONS }),
    populateLookupSelect('sim_device_type', { lookupType: LOOKUP_TYPES.deviceType, fallbackOptions: DEVICE_TYPE_OPTIONS, emptyOption: { value: '', label: 'Any device' } })
  ]);

  resetDeviceForm();
  resetAppForm();
  resetTrustForm();
  resetFixForm();
  resetSimulatorForm();
  await loadAll();

  document.getElementById('device_operator')?.addEventListener('change', () => {
    valueTypeVisibility();
    updateDevicePreview();
  });
  document.getElementById('device_value_type')?.addEventListener('change', () => {
    valueTypeVisibility();
    updateDevicePreview();
  });
  document.getElementById('devicePolicyResetBtn')?.addEventListener('click', resetDeviceForm);
  document.getElementById('appPolicyResetBtn')?.addEventListener('click', resetAppForm);
  document.getElementById('trustLevelResetBtn')?.addEventListener('click', resetTrustForm);
  document.getElementById('fixLibraryResetBtn')?.addEventListener('click', resetFixForm);
  document.getElementById('simulatorResetBtn')?.addEventListener('click', resetSimulatorForm);
  document.getElementById('installStarterPackBtn')?.addEventListener('click', async () => {
    clearError('devicePolicyFormError');
    clearError('appPolicyFormError');
    clearError('trustLevelFormError');
    clearError('fixLibraryFormError');
    setStarterPackMessage('');
    const confirmed = await confirmAction({
      title: 'Install baseline posture pack',
      message: 'Install the baseline posture pack for the current scope? Existing matching starter items will be left unchanged.',
      confirmLabel: 'Install pack',
      cancelLabel: 'Cancel',
      danger: false
    });
    if (!confirmed) return;
    try {
      const result = await apiFetch('/v1/policies/simple/starter-pack', {
        method: 'POST',
        headers: state.scope.getHeaders()
      });
      setStarterPackMessage(summarizeStarterPack(result), false);
      window.mdmToast?.('Baseline posture pack applied.');
      await loadAll();
    } catch (error) {
      setStarterPackMessage(error.message, true);
    }
  });

  [
    '#simpleDevicePolicyForm input',
    '#simpleDevicePolicyForm textarea',
    '#simpleDevicePolicyForm select'
  ].forEach((selector) => {
    document.querySelectorAll(selector).forEach((node) => node.addEventListener('input', updateDevicePreview));
    document.querySelectorAll(selector).forEach((node) => node.addEventListener('change', updateDevicePreview));
  });
  [
    '#simpleAppPolicyForm input',
    '#simpleAppPolicyForm textarea',
    '#simpleAppPolicyForm select'
  ].forEach((selector) => {
    document.querySelectorAll(selector).forEach((node) => node.addEventListener('input', updateAppPreview));
    document.querySelectorAll(selector).forEach((node) => node.addEventListener('change', updateAppPreview));
  });
  [
    '#simpleTrustLevelForm input',
    '#simpleTrustLevelForm textarea',
    '#simpleTrustLevelForm select'
  ].forEach((selector) => {
    document.querySelectorAll(selector).forEach((node) => node.addEventListener('input', updateTrustPreview));
    document.querySelectorAll(selector).forEach((node) => node.addEventListener('change', updateTrustPreview));
  });
  [
    '#simpleFixLibraryForm input',
    '#simpleFixLibraryForm textarea',
    '#simpleFixLibraryForm select'
  ].forEach((selector) => {
    document.querySelectorAll(selector).forEach((node) => node.addEventListener('input', updateFixPreview));
    document.querySelectorAll(selector).forEach((node) => node.addEventListener('change', updateFixPreview));
  });

  document.getElementById('addSimulatedAppBtn')?.addEventListener('click', () => {
    state.simulatedApps.push(createSimulatedApp());
    renderSimulatedApps();
  });

  document.getElementById('simpleDevicePolicyForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearError('devicePolicyFormError');
    setStarterPackMessage('');
    const editId = document.getElementById('device_policy_edit_id').value;
    const payload = {
      name: document.getElementById('device_policy_name').value,
      description: document.getElementById('device_policy_description').value,
      os_type: document.getElementById('device_os_type').value,
      device_type: document.getElementById('device_device_type').value || null,
      severity: Number.parseInt(document.getElementById('device_severity').value, 10),
      remediation_rule_id: document.getElementById('device_remediation_rule_id').value ? Number.parseInt(document.getElementById('device_remediation_rule_id').value, 10) : null,
      status: document.getElementById('device_status').value,
      field_name: document.getElementById('device_field_name').value,
      operator: document.getElementById('device_operator').value,
      value_type: document.getElementById('device_value_type').value
    };
    if (!['EXISTS', 'NOT_EXISTS'].includes(String(payload.operator).toUpperCase())) {
      if (payload.value_type === 'NUMBER') payload.value_numeric = Number.parseFloat(document.getElementById('device_value_numeric').value);
      if (payload.value_type === 'BOOLEAN') payload.value_boolean = document.getElementById('device_value_boolean').value === 'true';
      if (payload.value_type === 'TEXT') payload.value_text = document.getElementById('device_value_text').value;
    }
    try {
      await apiFetch(editId ? `/v1/policies/simple/device-checks/${encodeURIComponent(editId)}` : '/v1/policies/simple/device-checks', {
        method: editId ? 'PUT' : 'POST',
        headers: state.scope.getHeaders(),
        body: JSON.stringify(payload)
      });
      resetDeviceForm();
      await loadAll();
    } catch (error) {
      setError('devicePolicyFormError', error.message);
    }
  });

  document.getElementById('simpleAppPolicyForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearError('appPolicyFormError');
    setStarterPackMessage('');
    const editId = document.getElementById('app_policy_edit_id').value;
    const payload = {
      policy_tag: document.getElementById('app_policy_tag').value,
      app_os_type: document.getElementById('app_os_type').value,
      app_name: document.getElementById('app_name').value,
      package_id: document.getElementById('app_package_id').value || null,
      publisher: document.getElementById('app_publisher').value || null,
      min_allowed_version: document.getElementById('app_min_allowed_version').value || null,
      severity: Number.parseInt(document.getElementById('app_severity').value, 10),
      remediation_rule_id: document.getElementById('app_remediation_rule_id').value ? Number.parseInt(document.getElementById('app_remediation_rule_id').value, 10) : null,
      status: document.getElementById('app_status').value
    };
    try {
      await apiFetch(editId ? `/v1/policies/simple/app-rules/${encodeURIComponent(editId)}` : '/v1/policies/simple/app-rules', {
        method: editId ? 'PUT' : 'POST',
        headers: state.scope.getHeaders(),
        body: JSON.stringify(payload)
      });
      resetAppForm();
      await loadAll();
    } catch (error) {
      setError('appPolicyFormError', error.message);
    }
  });

  document.getElementById('simpleTrustLevelForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearError('trustLevelFormError');
    setStarterPackMessage('');
    const editId = document.getElementById('trust_policy_edit_id').value;
    const payload = {
      policy_name: document.getElementById('trust_policy_name').value,
      score_min: Number.parseInt(document.getElementById('trust_score_min').value, 10),
      score_max: Number.parseInt(document.getElementById('trust_score_max').value, 10),
      decision_action: document.getElementById('trust_decision_action').value,
      remediation_required: document.getElementById('trust_remediation_required').checked,
      response_message: document.getElementById('trust_response_message').value || null,
      status: document.getElementById('trust_status').value
    };
    try {
      await apiFetch(editId ? `/v1/policies/simple/trust-levels/${encodeURIComponent(editId)}` : '/v1/policies/simple/trust-levels', {
        method: editId ? 'PUT' : 'POST',
        headers: state.scope.getHeaders(),
        body: JSON.stringify(payload)
      });
      resetTrustForm();
      await loadAll();
    } catch (error) {
      setError('trustLevelFormError', error.message);
    }
  });

  document.getElementById('simpleFixLibraryForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearError('fixLibraryFormError');
    setStarterPackMessage('');
    const editId = document.getElementById('fix_edit_id').value;
    const payload = {
      remediation_code: editId ? document.getElementById('fix_existing_code').value : '',
      title: document.getElementById('fix_title').value,
      description: document.getElementById('fix_description').value,
      remediation_type: document.getElementById('fix_remediation_type').value,
      os_type: document.getElementById('fix_os_type').value || null,
      device_type: document.getElementById('fix_device_type').value || null,
      instruction_json: buildInstructionJson(
        document.getElementById('fix_title').value,
        document.getElementById('fix_description').value,
        document.getElementById('fix_steps').value
      ),
      priority: Number.parseInt(document.getElementById('fix_priority').value || '100', 10),
      status: document.getElementById('fix_status').value
    };
    try {
      await apiFetch(editId ? `/v1/policies/simple/fix-library/${encodeURIComponent(editId)}` : '/v1/policies/simple/fix-library', {
        method: editId ? 'PUT' : 'POST',
        headers: state.scope.getHeaders(),
        body: JSON.stringify(payload)
      });
      resetFixForm();
      await loadAll();
    } catch (error) {
      setError('fixLibraryFormError', error.message);
    }
  });

  document.getElementById('simplePolicySimulatorForm')?.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearError('policySimulatorError');
    try {
      state.simulationResult = await apiFetch('/v1/policies/simple/simulate', {
        method: 'POST',
        headers: state.scope.getHeaders(),
        body: JSON.stringify(collectSimulatorPayload())
      });
      renderSimulationResult();
    } catch (error) {
      setError('policySimulatorError', error.message);
    }
  });

  document.body.addEventListener('click', async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const { deviceEdit, deviceDelete, appEdit, appDelete, trustEdit, trustDelete, fixEdit, fixDelete, deviceTemplate, appTemplate, fixTemplate, jumpTarget, simAppDelete } = target.dataset;

    if (deviceTemplate) {
      applyDeviceTemplate(deviceTemplate);
    }
    if (appTemplate) {
      applyAppTemplate(appTemplate);
    }
    if (fixTemplate) {
      applyFixTemplate(fixTemplate);
    }
    if (jumpTarget) {
      jumpToSection(jumpTarget);
      return;
    }
    if (simAppDelete != null) {
      state.simulatedApps = state.simulatedApps.filter((_, index) => String(index) !== String(simAppDelete));
      renderSimulatedApps();
      return;
    }

    if (deviceEdit) fillDeviceForm(state.devicePolicies.find((row) => String(row.id) === String(deviceEdit)) || {});
    if (appEdit) fillAppForm(state.appPolicies.find((row) => String(row.id) === String(appEdit)) || {});
    if (trustEdit) fillTrustForm(state.trustLevels.find((row) => String(row.id) === String(trustEdit)) || {});
    if (fixEdit) fillFixForm(state.fixes.find((row) => String(row.id) === String(fixEdit)) || {});

    if (deviceDelete && await confirmDanger('Delete this device check?')) {
      await apiFetch(`/v1/policies/simple/device-checks/${encodeURIComponent(deviceDelete)}`, { method: 'DELETE', headers: state.scope.getHeaders() });
      await loadAll();
    }
    if (appDelete && await confirmDanger('Delete this app rule?')) {
      await apiFetch(`/v1/policies/simple/app-rules/${encodeURIComponent(appDelete)}`, { method: 'DELETE', headers: state.scope.getHeaders() });
      await loadAll();
    }
    if (trustDelete && await confirmDanger('Delete this trust level?')) {
      await apiFetch(`/v1/policies/simple/trust-levels/${encodeURIComponent(trustDelete)}`, { method: 'DELETE', headers: state.scope.getHeaders() });
      await loadAll();
    }
    if (fixDelete && await confirmDanger('Delete this fix instruction?')) {
      await apiFetch(`/v1/policies/simple/fix-library/${encodeURIComponent(fixDelete)}`, { method: 'DELETE', headers: state.scope.getHeaders() });
      await loadAll();
    }
  });

  document.body.addEventListener('input', (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const index = target.dataset?.simAppIndex;
    const field = target.dataset?.simAppField;
    if (index == null || !field || !state.simulatedApps[Number(index)]) {
      return;
    }
    state.simulatedApps[Number(index)][field] = target.value;
  });

  state.scope.onChange(async () => {
    setStarterPackMessage('');
    resetDeviceForm();
    resetAppForm();
    resetTrustForm();
    resetFixForm();
    resetSimulatorForm();
    await loadAll();
  });
});
