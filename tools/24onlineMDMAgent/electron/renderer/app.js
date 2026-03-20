const PINNED_BASE_URL = 'http://192.168.254.20:8080';
const textConfigFields = ['base_url', 'tenant_id', 'tenant_key', 'setup_key'];
const preferenceFields = ['launch_at_startup', 'desktop_shortcut'];

const uiState = {
  summary: null,
  running: false,
  installedAppQuery: ''
};

function byId(id) {
  return document.getElementById(id);
}

function setText(id, value) {
  byId(id).textContent = value ?? '-';
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function formatDateTime(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

function formatShort(value, maxLength = 18) {
  if (!value) {
    return '-';
  }
  const text = String(value);
  return text.length <= maxLength ? text : `${text.slice(0, maxLength)}...`;
}

function setStatus(message) {
  byId('status_line').textContent = message;
}

function statusToneClass(tone) {
  switch (tone) {
    case 'healthy':
      return 'tone-healthy';
    case 'warning':
      return 'tone-warning';
    case 'critical':
      return 'tone-critical';
    case 'inactive':
      return 'tone-neutral';
    default:
      return 'tone-monitoring';
  }
}

function heroBadgeLabel(compliance) {
  switch (compliance?.attention_state) {
    case 'healthy':
      return 'Protected';
    case 'warning':
      return 'Action Needed';
    case 'critical':
      return 'Restricted';
    case 'inactive':
      return 'Setup Needed';
    default:
      return 'Monitoring';
  }
}

function applyPill(id, text, tone) {
  const el = byId(id);
  el.textContent = text;
  el.className = `status-pill ${statusToneClass(tone)}`;
}

function endpointLocked(summary) {
  return Boolean(summary?.organization?.base_url_locked || summary?.runtime?.buildProfile?.baseUrlLocked);
}

function applyConfig(config, summary) {
  for (const field of textConfigFields) {
    byId(field).value = config?.[field] ?? '';
  }
  for (const field of preferenceFields) {
    byId(field).checked = Boolean(config?.[field]);
  }

  const locked = endpointLocked(summary);
  const baseUrlRow = byId('base_url_row');
  const endpointLock = byId('endpoint_lock');
  const baseUrlInput = byId('base_url');
  const endpointNote = byId('endpoint_note');
  const baseUrl = config?.base_url || summary?.runtime?.buildProfile?.baseUrl || '';

  baseUrlInput.readOnly = locked;
  baseUrlInput.disabled = locked;
  baseUrlRow.hidden = locked;
  endpointLock.hidden = !locked;
  setText('embedded_base_url', baseUrl || '-');
  endpointNote.textContent = locked
    ? 'This build is pinned to the managed 24online service endpoint. Only tenant credentials can be changed from this device.'
    : 'Provide the organization endpoint and tenant credentials once. The agent derives device identity, enrollment state, and posture data locally.';
}

function buildConfigFromUi() {
  const config = {};
  for (const field of textConfigFields) {
    config[field] = byId(field).value;
  }
  for (const field of preferenceFields) {
    config[field] = byId(field).checked;
  }
  if (uiState.summary?.paths?.configPath) {
    config.__configPath = uiState.summary.paths.configPath;
  }
  return config;
}

function validateBaseUrl(baseUrl) {
  if (!baseUrl || !String(baseUrl).trim()) {
    throw new Error('Base URL is required.');
  }
  let parsed;
  try {
    parsed = new URL(baseUrl);
  } catch {
    throw new Error('Base URL must be a valid http or https URL.');
  }
  const host = parsed.hostname.toLowerCase();
  if (parsed.protocol === 'https:') {
    return;
  }
  if (parsed.protocol === 'http:' && parsed.toString().replace(/\/$/, '') === PINNED_BASE_URL) {
    return;
  }
  if (parsed.protocol === 'http:' && (host === 'localhost' || host === '127.0.0.1')) {
    return;
  }
  throw new Error('Base URL must use https unless it targets localhost, 127.0.0.1, or the managed 24online endpoint.');
}

function validateForManagedAction(mode) {
  const config = buildConfigFromUi();
  validateBaseUrl(config.base_url);
  if (!config.tenant_id || !config.tenant_id.trim()) {
    throw new Error('Tenant ID is required.');
  }
  if (!config.tenant_key || !config.tenant_key.trim()) {
    throw new Error('Tenant key is required.');
  }
  const enrolled = Boolean(uiState.summary?.device?.device_external_id);
  if ((mode === 'claim-only' || !enrolled) && (!config.setup_key || !config.setup_key.trim())) {
    throw new Error('Setup key is required until the device has been provisioned.');
  }
  return config;
}

function setRunning(running) {
  uiState.running = running;
  for (const id of [
    'save_connection',
    'activate_protection',
    'provision_device',
    'refresh_profile',
    'refresh_overview',
    'run_preview',
    'save_run_preferences'
  ]) {
    byId(id).disabled = running;
  }
  for (const id of preferenceFields) {
    byId(id).disabled = running;
  }
}

function renderWarnings(warnings) {
  const warningBox = byId('warning_box');
  const list = byId('validation_warnings');
  if (!Array.isArray(warnings) || warnings.length === 0) {
    warningBox.hidden = true;
    list.innerHTML = '';
    return;
  }
  warningBox.hidden = false;
  list.innerHTML = warnings.map(item => `<li>${escapeHtml(item)}</li>`).join('');
}

function renderAppSamples(device) {
  const container = byId('installed_app_list');
  const resultLabel = byId('installed_app_results');
  const installedApps = Array.isArray(device?.installed_apps) ? device.installed_apps : [];
  const query = uiState.installedAppQuery.trim().toLowerCase();

  if (installedApps.length === 0) {
    resultLabel.textContent = 'No applications collected yet';
    container.innerHTML = '<div class="empty-state compact">No installed application inventory has been collected yet.</div>';
    return;
  }

  const filteredApps = installedApps.filter(item => {
    if (!query) {
      return true;
    }
    const haystack = [
      item?.app_name,
      item?.publisher,
      item?.package_id,
      item?.app_version,
      item?.install_source
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return haystack.includes(query);
  });

  resultLabel.textContent = query
    ? `${filteredApps.length} of ${installedApps.length} applications`
    : `${installedApps.length} applications`;

  if (filteredApps.length === 0) {
    container.innerHTML = '<div class="empty-state compact">No installed applications match the current search.</div>';
    return;
  }

  container.innerHTML = filteredApps.map(item => {
    const appName = item?.app_name || 'Application';
    const publisher = item?.publisher || 'Unknown publisher';
    const icon = buildAppIcon(appName);
    const versionParts = [item?.app_version, item?.status].filter(Boolean);
    const metaParts = [publisher, item?.install_source].filter(Boolean);
    return `
      <article class="app-list-item">
        <img class="app-icon" src="${icon}" alt="${escapeHtml(appName)} icon">
        <div class="app-copy">
          <div class="app-topline">
            <strong>${escapeHtml(appName)}</strong>
            ${versionParts.length ? `<span class="app-version">${escapeHtml(versionParts.join(' · '))}</span>` : ''}
          </div>
          <div class="app-meta">${escapeHtml(metaParts.join(' · ') || publisher)}</div>
          ${item?.package_id ? `<code class="app-package-id">${escapeHtml(item.package_id)}</code>` : ''}
        </div>
      </article>
    `;
  }).join('');
}

function colorFromText(value) {
  const palette = [
    ['#0f7567', '#2f5fa7'],
    ['#2457a6', '#4b83cb'],
    ['#8b5a1c', '#d29439'],
    ['#7d2e5f', '#b34b8d'],
    ['#40617d', '#6f92b0'],
    ['#1b6b4f', '#2aa47a']
  ];
  let hash = 0;
  for (const char of String(value || 'app')) {
    hash = ((hash << 5) - hash) + char.charCodeAt(0);
    hash |= 0;
  }
  return palette[Math.abs(hash) % palette.length];
}

function appInitials(name) {
  const words = String(name || '')
    .replace(/[^a-zA-Z0-9 ]+/g, ' ')
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (words.length === 0) {
    return 'A';
  }
  if (words.length === 1) {
    return words[0].slice(0, 2).toUpperCase();
  }
  return `${words[0][0] || ''}${words[1][0] || ''}`.toUpperCase();
}

function buildAppIcon(name) {
  const initials = appInitials(name);
  const [startColor, endColor] = colorFromText(name);
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96" role="img" aria-label="${escapeHtml(name)}">
      <defs>
        <linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="${startColor}"/>
          <stop offset="100%" stop-color="${endColor}"/>
        </linearGradient>
      </defs>
      <rect x="6" y="6" width="84" height="84" rx="24" fill="url(#g)"/>
      <text x="48" y="56" text-anchor="middle" font-family="Segoe UI, Aptos, sans-serif" font-size="28" font-weight="700" fill="#ffffff">${escapeHtml(initials)}</text>
    </svg>
  `.trim();
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function renderRemediation(compliance) {
  const container = byId('remediation_panel');
  const remediation = Array.isArray(compliance?.remediation) ? compliance.remediation : [];
  if (remediation.length === 0) {
    container.className = 'empty-state';
    container.textContent = 'No active remediation guidance. Once the device receives policy guidance, recommended actions will appear here.';
    return;
  }

  container.className = 'remediation-list';
  container.innerHTML = remediation.map(item => `
    <article class="remediation-item">
      <div class="remediation-top">
        <strong>${escapeHtml(item.title || 'Remediation')}</strong>
        <span class="status-pill ${statusToneClass(item.remediation_status === 'RESOLVED_ON_RESCAN' ? 'healthy' : 'warning')}">${escapeHtml(item.remediation_status || 'OPEN')}</span>
      </div>
      <p>${escapeHtml(item.description || 'Follow the recommended action to restore compliance.')}</p>
      <div class="remediation-meta">
        <span>${escapeHtml(item.remediation_type || 'ACTION')}</span>
        <span>${escapeHtml(item.enforce_mode || 'GUIDED')}</span>
      </div>
    </article>
  `).join('');
}

function renderActivity(activity) {
  const container = byId('activity_timeline');
  if (!Array.isArray(activity) || activity.length === 0) {
    container.innerHTML = '<div class="empty-state compact">No managed-device activity yet.</div>';
    return;
  }
  container.innerHTML = activity.map(item => `
    <article class="timeline-item">
      <div class="timeline-marker ${statusToneClass(item.tone)}"></div>
      <div class="timeline-copy">
        <div class="timeline-top">
          <strong>${escapeHtml(item.title)}</strong>
          <span>${escapeHtml(formatDateTime(item.at))}</span>
        </div>
        <p>${escapeHtml(item.detail || '')}</p>
      </div>
    </article>
  `).join('');
}

function renderSupport(summary) {
  const snapshot = {
    organization: summary.organization,
    device: {
      device_external_id: summary.device?.device_external_id || null,
      host_name: summary.device?.host_name || null,
      os_type: summary.device?.os_type || null,
      os_name: summary.device?.os_name || null,
      os_version: summary.device?.os_version || null,
      installed_app_count: summary.device?.installed_app_count || 0
    },
    compliance: summary.compliance,
    activity: summary.activity,
    support: summary.support,
    runtime: summary.runtime
  };
  byId('support_snapshot').textContent = `${JSON.stringify(snapshot, null, 2)}\n`;
}

function renderSecurity(runtimeInfo) {
  const security = runtimeInfo?.security || {};
  let statusText = 'Unavailable';
  let noteText = 'Secure credential storage is not available on this device.';

  if (security.persistent) {
    statusText = security.backend ? `Protected (${security.backend})` : 'Protected';
    noteText = 'Tenant credentials are stored using protected OS-backed secret storage.';
  } else if (security.available) {
    statusText = security.backend ? `Limited (${security.backend})` : 'Limited';
    noteText = 'Sensitive credentials cannot be persisted securely until a stronger OS key store is available.';
  }

  setText('secret_storage_status', statusText);
  setText('secret_storage_note', noteText);
}

function renderRunApplication(summary) {
  const runtimePreferences = summary.runtime?.preferences || {};
  const statuses = [];
  statuses.push(runtimePreferences.launchAtStartupEnabled ? 'Startup enabled' : 'Startup disabled');
  statuses.push(runtimePreferences.desktopShortcutEnabled ? 'Shortcut available' : 'Shortcut not created');
  byId('run_application_status').textContent = statuses.join(' · ');
}

function renderSummary(summary) {
  uiState.summary = summary;
  applyConfig(summary.config || {}, summary);

  const device = summary.device || {};
  const compliance = summary.compliance || {};
  const runtime = summary.runtime || {};

  setText('hero_tenant', summary.organization?.tenant_id || 'Unassigned');
  setText('hero_device', device.device_external_id || device.host_name || 'Not provisioned');
  applyPill('hero_badge', heroBadgeLabel(compliance), compliance.attention_state || 'monitoring');
  applyPill(
    'connection_badge',
    summary.organization?.configured ? 'Configured' : 'Setup needed',
    summary.organization?.configured ? 'healthy' : 'inactive'
  );
  applyPill('sync_status_badge', compliance.sync_status || 'NOT_STARTED', compliance.attention_state || 'monitoring');
  applyPill('device_profile_badge', device.captured_at ? 'Current' : 'Pending', device.captured_at ? 'healthy' : 'monitoring');

  setText('hero_message', compliance.detail || 'Managed device overview is loading.');
  setText('summary_enrollment', device.enrollment_no || device.device_external_id || 'Not provisioned');
  setText('summary_token_expires', formatDateTime(device.token_expires_at));
  setText('summary_payload_id', compliance.last_payload_id ?? '-');
  setText('summary_decision_response', compliance.last_decision_response_id ?? '-');
  setText('decision_reason', compliance.decision_reason || 'No policy decision has been recorded yet.');

  const score = Number.isFinite(compliance.trust_score) ? compliance.trust_score : null;
  const ring = byId('score_ring');
  ring.className = `score-ring ${statusToneClass(compliance.attention_state || 'monitoring')}`;
  ring.style.setProperty('--score-angle', `${Math.max(0, Math.min(100, score ?? 0)) * 3.6}deg`);
  setText('score_value', score === null ? '--' : String(score));
  setText('score_band', compliance.trust_band || 'UNKNOWN');
  setText('compliance_headline', compliance.headline || 'Protection posture unavailable');
  setText('compliance_detail', compliance.detail || 'The device has not completed a managed sync yet.');
  setText('decision_action', compliance.decision_action || 'Pending');
  setText('remediation_count', String(compliance.remediation_count ?? 0));
  setText('schema_status', compliance.schema_compatibility_status || 'Not reported');
  setText('last_sync', formatDateTime(compliance.last_sync_at));
  renderWarnings(compliance.validation_warnings);

  setText('device_host_name', device.host_name || '-');
  setText('device_external_id', device.device_external_id || '-');
  setText('device_platform', device.os_type || device.platform || '-');
  setText('device_type', device.device_type || '-');
  setText('device_os', [device.os_name, device.os_version].filter(Boolean).join(' ') || '-');
  setText('device_build', device.os_build_number || device.kernel_version || '-');
  setText('device_manufacturer', device.manufacturer || '-');
  setText('device_model', device.model || '-');
  setText('device_time_zone', device.time_zone || '-');
  setText('device_fingerprint', formatShort(device.device_fingerprint, 20));
  setText('device_agent_version', device.agent_version || '-');
  setText('device_captured_at', formatDateTime(device.captured_at));
  setText('installed_app_count', String(device.installed_app_count ?? 0));

  setText('signal_emulator', device.running_on_emulator ? 'Detected' : 'No');
  setText('signal_root', device.root_detected ? 'Yes' : 'No');
  setText('runtime_mode', runtime.isPackaged ? 'Packaged' : 'Development');
  setText('runtime_arch', `${runtime.platform || '-'} / ${runtime.arch || '-'}`);
  setText('runtime_asset_root', runtime?.preferences?.startupHidden ? 'Tray startup' : 'Pinned endpoint');
  byId('runtime_chip').textContent = runtime.isPackaged ? 'Packaged runtime' : 'Development runtime';

  setText('path_config', summary.paths?.configPath || '-');
  setText('path_state', summary.paths?.statePath || '-');
  setText('path_output', summary.paths?.outputDir || '-');

  renderAppSamples(device);
  renderRemediation(compliance);
  renderActivity(summary.activity || []);
  renderSupport(summary);
  renderSecurity(runtime);
  renderRunApplication(summary);
}

function appendLog(text, stream = 'stdout') {
  const target = byId('run_log');
  target.textContent += stream === 'stderr' ? `[stderr] ${text}` : text;
  target.scrollTop = target.scrollHeight;
}

async function refreshOverview() {
  const summary = await window.agentApp.load(uiState.summary?.paths?.configPath);
  renderSummary(summary);
}

async function refreshDeviceProfile() {
  const preview = await window.agentApp.previewDevice();
  if (!uiState.summary) {
    return;
  }
  uiState.summary.device = {
    ...(uiState.summary.device || {}),
    ...preview
  };
  renderSummary(uiState.summary);
}

async function saveConfig() {
  const draft = buildConfigFromUi();
  validateBaseUrl(draft.base_url);
  const summary = await window.agentApp.saveConfig(draft);
  renderSummary(summary);
  setStatus(`Access settings saved for tenant ${summary.organization?.tenant_id || '-'}.`);
}

async function saveRunPreferences() {
  const summary = await window.agentApp.saveRunPreferences(buildConfigFromUi());
  renderSummary(summary);
  setStatus('Run application settings updated.');
}

async function runAgent(mode) {
  validateForManagedAction(mode);
  await saveConfig();
  byId('run_log').textContent = '';
  setRunning(true);
  setStatus(mode === 'live' ? 'Activating device protection...' : 'Starting managed operation...');
  await window.agentApp.run({
    mode,
    configPath: uiState.summary.paths.configPath
  });
}

function syncSensitiveInputsVisibility() {
  const visible = byId('show_sensitive_values').checked;
  byId('tenant_key').type = visible ? 'text' : 'password';
  byId('setup_key').type = visible ? 'text' : 'password';
}

function bindUi() {
  byId('installed_app_search').addEventListener('input', event => {
    uiState.installedAppQuery = event.target.value || '';
    renderAppSamples(uiState.summary?.device || {});
  });

  byId('save_run_preferences').addEventListener('click', async () => {
    try {
      await saveRunPreferences();
    } catch (error) {
      setStatus(error.message);
      appendLog(`${error.stack || error.message}\n`, 'stderr');
    }
  });

  byId('save_connection').addEventListener('click', async () => {
    try {
      await saveConfig();
    } catch (error) {
      setStatus(error.message);
      appendLog(`${error.stack || error.message}\n`, 'stderr');
    }
  });

  byId('activate_protection').addEventListener('click', async () => {
    try {
      await runAgent('live');
    } catch (error) {
      setRunning(false);
      setStatus(error.message);
      appendLog(`${error.stack || error.message}\n`, 'stderr');
    }
  });

  byId('provision_device').addEventListener('click', async () => {
    try {
      await runAgent('claim-only');
    } catch (error) {
      setRunning(false);
      setStatus(error.message);
      appendLog(`${error.stack || error.message}\n`, 'stderr');
    }
  });

  byId('refresh_profile').addEventListener('click', async () => {
    try {
      await refreshDeviceProfile();
      setStatus('Device profile refreshed.');
    } catch (error) {
      setStatus(error.message);
      appendLog(`${error.stack || error.message}\n`, 'stderr');
    }
  });

  byId('refresh_overview').addEventListener('click', async () => {
    try {
      await refreshOverview();
      setStatus('Managed device overview refreshed.');
    } catch (error) {
      setStatus(error.message);
      appendLog(`${error.stack || error.message}\n`, 'stderr');
    }
  });

  byId('run_preview').addEventListener('click', async () => {
    try {
      await runAgent('dry-run');
    } catch (error) {
      setRunning(false);
      setStatus(error.message);
      appendLog(`${error.stack || error.message}\n`, 'stderr');
    }
  });

  byId('open_output').addEventListener('click', async () => {
    if (uiState.summary?.paths?.configPath) {
      await window.agentApp.openOutput(uiState.summary.paths.configPath);
    }
  });

  byId('open_config_dir').addEventListener('click', async () => {
    if (uiState.summary?.paths?.configPath) {
      await window.agentApp.openConfigDir(uiState.summary.paths.configPath);
    }
  });

  byId('clear_log').addEventListener('click', () => {
    byId('run_log').textContent = '';
  });

  byId('show_sensitive_values').addEventListener('change', syncSensitiveInputsVisibility);
}

window.agentApp.onRunLog(payload => {
  appendLog(payload.chunk, payload.stream);
});

window.agentApp.onRunState(async payload => {
  if (payload.phase === 'started') {
    setStatus(payload.mode === 'live' ? 'Protection sync in progress...' : `Running ${payload.mode}...`);
    return;
  }

  if (payload.phase === 'failed') {
    setRunning(false);
    setStatus(payload.message || `Failed to start ${payload.mode}`);
    appendLog(`${payload.message || 'Unknown failure'}\n`, 'stderr');
    return;
  }

  if (payload.phase === 'finished') {
    setRunning(false);
    if (payload.summary) {
      renderSummary(payload.summary);
    } else {
      await refreshOverview();
    }
    setStatus(
      payload.exitCode === 0
        ? (payload.mode === 'live' ? 'Protection sync completed successfully.' : 'Managed operation completed successfully.')
        : `${payload.mode} exited with code ${payload.exitCode}.`
    );
  }
});

async function init() {
  bindUi();
  await refreshOverview();
  syncSensitiveInputsVisibility();
  try {
    await refreshDeviceProfile();
  } catch (error) {
    appendLog(`${error.stack || error.message}\n`, 'stderr');
  }
  setStatus('Ready');
}

init().catch(error => {
  setStatus(error.message);
  appendLog(`${error.stack || error.message}\n`, 'stderr');
});
