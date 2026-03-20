import { apiFetch } from '../api.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';
import { initTimelineViewer } from './device-timeline.js';

const OS_TYPE_OPTIONS = ['ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'];
const SCORE_BAND_OPTIONS = ['TRUSTED', 'LOW_RISK', 'MEDIUM_RISK', 'HIGH_RISK', 'CRITICAL'];
const HISTORY_PAGE_SIZE = 50;

function bandClass(band) {
  const normalized = String(band || '').trim().toUpperCase();
  if (normalized === 'TRUSTED') return 'badge badge--trusted';
  if (normalized === 'LOW_RISK') return 'badge badge--low-risk';
  if (normalized === 'MEDIUM_RISK') return 'badge badge--medium-risk';
  if (normalized === 'HIGH_RISK') return 'badge badge--high-risk';
  if (normalized === 'CRITICAL') return 'badge badge--critical';
  return 'badge';
}

function esc(value) {
  return sanitizeDisplayText(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function sanitizeDisplayText(value) {
  return String(value ?? '')
    .replaceAll('\uFFFD', '')
    .replaceAll('\u00EF\u00BF\u00BD', '')
    .replaceAll('\u00C3\u00AF\u00C2\u00BF\u00C2\u00BD', '')
    .replaceAll('\u00E2\u20AC\u00A6', '...')
    .replaceAll('\u00C3\u00A2\u00E2\u201A\u00AC\u00C2\u00A6', '...')
    .replaceAll('\u00E2\u20AC\u201C', '-')
    .replaceAll('\u00C3\u00A2\u00E2\u201A\u00AC\u00E2\u20AC\u015C', '-')
    .replaceAll('\u00E2\u20AC\u201D', '-')
    .replaceAll('\u00C3\u00A2\u00E2\u201A\u00AC\u00E2\u20AC\u009D', '-')
    .trim();
}

function pick(obj, ...keys) {
  if (!obj) return undefined;
  for (const key of keys) {
    if (obj[key] !== undefined && obj[key] !== null) {
      return obj[key];
    }
  }
  return undefined;
}

function textOrDash(value) {
  if (value === null || value === undefined) return '---';
  const text = sanitizeDisplayText(value);
  return text ? text : '---';
}

function renderBandBadge(band) {
  const label = textOrDash(band);
  if (label === '---') {
    return esc(label);
  }
  return `<span class="${bandClass(label)}">${esc(label)}</span>`;
}

function boolText(value) {
  if (value === null || value === undefined) return '---';
  return value ? 'true' : 'false';
}

function shortHash(hash) {
  const value = String(hash || '');
  if (!value) return '---';
  if (value.length <= 20) return value;
  return `${value.slice(0, 10)}...${value.slice(-8)}`;
}

function clip(value, maxLen) {
  const text = sanitizeDisplayText(value);
  if (!text) return '---';
  if (text.length <= maxLen) return text;
  return `${text.slice(0, maxLen - 3)}...`;
}

function fmtDate(value) {
  if (!value) return '---';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return textOrDash(value);
  return date.toLocaleString();
}

function prettyJson(value) {
  if (value === null || value === undefined) {
    return 'No data.';
  }
  if (typeof value === 'string') {
    const text = sanitizeDisplayText(value);
    if (!text) return 'No data.';
    try {
      return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
      return text;
    }
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(JSON.parse(sanitizeDisplayText(JSON.stringify(value))), null, 2);
    } catch {
      return sanitizeDisplayText(String(value));
    }
  }
  return sanitizeDisplayText(String(value));
}

function settledArray(result) {
  if (result.status !== 'fulfilled' || !Array.isArray(result.value)) {
    return [];
  }
  return result.value;
}

function settledObject(result) {
  if (result.status !== 'fulfilled') {
    return null;
  }
  return result.value ?? null;
}

function renderKeyValues(container, pairs) {
  if (!container) return;

  container.replaceChildren();

  for (const pair of pairs) {
    const keyDiv = document.createElement('div');
    keyDiv.className = 'k';
    keyDiv.textContent = textOrDash(pair.label);

    const valueDiv = document.createElement('div');
    valueDiv.className = 'v';
    valueDiv.textContent = textOrDash(pair.value);

    container.append(keyDiv, valueDiv);
  }
}

function renderTableBody(tbody, rows, columns, colSpan) {
  if (!tbody) return;
  if (!rows.length) {
    tbody.innerHTML = `<tr><td colspan="${colSpan}" class="muted">No rows.</td></tr>`;
    return;
  }
  tbody.innerHTML = rows.map((row) => `
    <tr>${columns.map((column) => `<td>${esc(column(row))}</td>`).join('')}</tr>
  `).join('');
}

document.addEventListener('DOMContentLoaded', async () => {
  await Promise.all([
    populateLookupSelect('osType', {
      lookupType: LOOKUP_TYPES.osType,
      fallbackOptions: OS_TYPE_OPTIONS,
      emptyOption: { value: '', label: 'Any' }
    }),
    populateLookupSelect('scoreBand', {
      lookupType: LOOKUP_TYPES.scoreBand,
      fallbackOptions: SCORE_BAND_OPTIONS,
      emptyOption: { value: '', label: 'Any' }
    })
  ]);

  const detailElements = {
    caption: document.getElementById('device-detail-caption'),
    loading: document.getElementById('device-detail-loading'),
    error: document.getElementById('device-detail-error'),
    content: document.getElementById('device-detail-content'),
    summary: document.getElementById('device-summary-row'),
    profileMeta: document.getElementById('device-trust-profile-meta'),
    payloadMeta: document.getElementById('device-latest-payload-meta'),
    snapshot: document.getElementById('device-latest-snapshot'),
    decision: document.getElementById('device-latest-decision'),
    decisionsBody: document.getElementById('device-decisions-history-body'),
    payloadBody: document.getElementById('device-payload-history-body'),
    eventsBody: document.getElementById('device-events-history-body'),
    appsBody: document.getElementById('device-apps-history-body'),
    runsBody: document.getElementById('device-runs-history-body'),
    runMatchesBody: document.getElementById('device-run-matches-body'),
    runRemediationsBody: document.getElementById('device-run-remediation-body'),
    rawPayload: document.getElementById('device-raw-payload'),
    rawDecision: document.getElementById('device-raw-decision'),
    rawRunResponse: document.getElementById('device-raw-run-response'),
    tenantOverride: document.getElementById('detailTenantId'),
    refreshButton: document.getElementById('refresh-device-detail')
  };

  const state = {
    selectedRowEl: null,
    selectedDeviceId: null,
    selectedTenantId: null,
    selectedRowData: null
  };

  const historyState = {
    apps: { page: 0, hasMore: false },
    decisions: { page: 0, hasMore: false },
    payloads: { page: 0, hasMore: false },
    events: { page: 0, hasMore: false },
    runs: { page: 0, hasMore: false }
  };

  const historyConfig = {
    apps: {
      tbody: detailElements.appsBody,
      colSpan: 8,
      statusId: 'device-apps-history-status',
      fetch: (deviceId, headers, page) =>
        apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/installed-apps?page=${page}&size=${HISTORY_PAGE_SIZE}`, { headers }),
      render: (rows) => renderTableBody(
        detailElements.appsBody,
        rows,
        [
          (row) => pick(row, 'app_name', 'appName'),
          (row) => pick(row, 'package_id', 'packageId'),
          (row) => pick(row, 'app_version', 'appVersion'),
          (row) => pick(row, 'latest_available_version', 'latestAvailableVersion'),
          (row) => pick(row, 'status'),
          (row) => pick(row, 'install_source', 'installSource'),
          (row) => boolText(pick(row, 'is_system_app', 'systemApp')),
          (row) => fmtDate(pick(row, 'capture_time', 'captureTime'))
        ],
        8
      )
    },
    decisions: {
      tbody: detailElements.decisionsBody,
      colSpan: 8,
      statusId: 'device-decisions-history-status',
      fetch: (deviceId, headers, page) =>
        apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/decisions?page=${page}&size=${HISTORY_PAGE_SIZE}`, { headers }),
      render: (rows) => renderTableBody(
        detailElements.decisionsBody,
        rows,
        [
          (row) => pick(row, 'decision_action', 'decisionAction'),
          (row) => pick(row, 'trust_score', 'trustScore'),
          (row) => boolText(pick(row, 'remediation_required', 'remediationRequired')),
          (row) => pick(row, 'delivery_status', 'deliveryStatus'),
          (row) => fmtDate(pick(row, 'sent_at', 'sentAt')),
          (row) => fmtDate(pick(row, 'acknowledged_at', 'acknowledgedAt')),
          (row) => clip(pick(row, 'error_message', 'errorMessage'), 48),
          (row) => fmtDate(pick(row, 'created_at', 'createdAt'))
        ],
        8
      )
    },
    payloads: {
      tbody: detailElements.payloadBody,
      colSpan: 8,
      statusId: 'device-payloads-history-status',
      fetch: (deviceId, headers, page) =>
        apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/posture-payloads?page=${page}&size=${HISTORY_PAGE_SIZE}`, { headers }),
      render: (rows) => renderTableBody(
        detailElements.payloadBody,
        rows,
        [
          (row) => pick(row, 'id'),
          (row) => pick(row, 'agent_id', 'agentId'),
          (row) => pick(row, 'process_status', 'processStatus'),
          (row) => pick(row, 'payload_version', 'payloadVersion'),
          (row) => shortHash(pick(row, 'payload_hash', 'payloadHash')),
          (row) => fmtDate(pick(row, 'received_at', 'receivedAt')),
          (row) => fmtDate(pick(row, 'processed_at', 'processedAt')),
          (row) => clip(pick(row, 'process_error', 'processError'), 48)
        ],
        8
      )
    },
    events: {
      tbody: detailElements.eventsBody,
      colSpan: 8,
      statusId: 'device-events-history-status',
      fetch: (deviceId, headers, page) =>
        apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/trust-score-events?page=${page}&size=${HISTORY_PAGE_SIZE}`, { headers }),
      render: (rows) => renderTableBody(
        detailElements.eventsBody,
        rows,
        [
          (row) => pick(row, 'event_source', 'eventSource'),
          (row) => pick(row, 'score_before', 'scoreBefore'),
          (row) => pick(row, 'score_delta', 'scoreDelta'),
          (row) => pick(row, 'score_after', 'scoreAfter'),
          (row) => pick(row, 'os_lifecycle_state', 'osLifecycleState'),
          (row) => pick(row, 'source_record_id', 'sourceRecordId'),
          (row) => clip(pick(row, 'notes'), 72),
          (row) => fmtDate(pick(row, 'event_time', 'eventTime'))
        ],
        8
      )
    },
    runs: {
      tbody: detailElements.runsBody,
      colSpan: 9,
      statusId: 'device-runs-history-status',
      fetch: (deviceId, headers, page) =>
        apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/evaluation-runs?page=${page}&size=${HISTORY_PAGE_SIZE}`, { headers }),
      render: (rows) => renderTableBody(
        detailElements.runsBody,
        rows,
        [
          (row) => pick(row, 'id'),
          (row) => pick(row, 'device_posture_payload_id', 'devicePosturePayloadId'),
          (row) => pick(row, 'evaluation_status', 'evaluationStatus'),
          (row) => pick(row, 'decision_action', 'decisionAction'),
          (row) => pick(row, 'trust_score_before', 'trustScoreBefore'),
          (row) => pick(row, 'trust_score_after', 'trustScoreAfter'),
          (row) => pick(row, 'matched_rule_count', 'matchedRuleCount'),
          (row) => pick(row, 'matched_app_count', 'matchedAppCount'),
          (row) => fmtDate(pick(row, 'evaluated_at', 'evaluatedAt'))
        ],
        9
      )
    }
  };

  function resolveDetailAccess(deviceRow = state.selectedRowData) {
    const tenantFromRow = String(pick(deviceRow, 'tenant_id', 'tenantId') || '').trim();
    const tenantFromInput = (detailElements.tenantOverride?.value || '').trim().toLowerCase();
    const effectiveTenant = tenantFromInput || tenantFromRow;
    return {
      effectiveTenant,
      headers: effectiveTenant ? { 'X-Tenant-Id': effectiveTenant } : {}
    };
  }

  function setSelectedDeviceUrl(deviceId, tenantId) {
    const url = new URL(window.location.href);
    if (deviceId) {
      url.searchParams.set('device_external_id', deviceId);
    } else {
      url.searchParams.delete('device_external_id');
    }
    if (tenantId) {
      url.searchParams.set('tenant_id', tenantId);
    } else {
      url.searchParams.delete('tenant_id');
    }
    window.history.replaceState({}, '', `${url.pathname}${url.search}`);
  }

  function historyButtons(key) {
    return {
      prev: document.querySelector(`[data-history-nav="${key}"][data-direction="prev"]`),
      next: document.querySelector(`[data-history-nav="${key}"][data-direction="next"]`)
    };
  }

  function updateHistoryControls(key, rowCount, statusText = '') {
    const config = historyConfig[key];
    const sectionState = historyState[key];
    if (!config || !sectionState) {
      return;
    }
    const statusEl = document.getElementById(config.statusId);
    if (statusEl) {
      statusEl.textContent = statusText || `Page ${sectionState.page + 1} | ${rowCount} row${rowCount === 1 ? '' : 's'}`;
    }
    const { prev, next } = historyButtons(key);
    if (prev) {
      prev.disabled = sectionState.page <= 0;
    }
    if (next) {
      next.disabled = !sectionState.hasMore;
    }
  }

  function applyHistoryRows(key, rows, page) {
    const config = historyConfig[key];
    const sectionState = historyState[key];
    if (!config || !sectionState) {
      return;
    }
    sectionState.page = page;
    sectionState.hasMore = Array.isArray(rows) && rows.length === HISTORY_PAGE_SIZE;
    config.render(Array.isArray(rows) ? rows : []);
    updateHistoryControls(key, Array.isArray(rows) ? rows.length : 0);
  }

  function setHistoryError(key, message) {
    const config = historyConfig[key];
    const sectionState = historyState[key];
    if (!config || !sectionState || !config.tbody) {
      return;
    }
    sectionState.hasMore = false;
    config.tbody.innerHTML = `<tr><td colspan="${config.colSpan}" class="muted">Failed to load rows: ${esc(message || 'Unknown error')}</td></tr>`;
    updateHistoryControls(key, 0, 'Unavailable');
  }

  function resetHistorySections() {
    Object.keys(historyConfig).forEach((key) => {
      historyState[key].page = 0;
      historyState[key].hasMore = false;
      historyConfig[key].render([]);
      updateHistoryControls(key, 0);
    });
  }

  async function loadHistoryPage(key, page) {
    if (!state.selectedDeviceId || !historyConfig[key]) {
      return;
    }
    const { headers } = resolveDetailAccess();
    const rows = await historyConfig[key].fetch(state.selectedDeviceId, headers, page);
    applyHistoryRows(key, Array.isArray(rows) ? rows : [], page);
  }

  const dt = window.mdmInitDataTable('#device-table', {
    ajax: { url: '/ui/datatables/device-trust-profiles', dataSrc: 'data' },
    defaultSortBy: 'id',
    defaultSortDir: 'desc',
    extraParams: () => ({
      os_type: document.getElementById('osType')?.value || '',
      score_band: document.getElementById('scoreBand')?.value || ''
    }),
    columns: [
      { data: 'device_external_id' },
      {
        data: 'os_type',
        render: (value, type, row, helpers) => {
          const osType = helpers.escapeHtml(row.os_type || '');
          const osName = row.os_name ? ` / ${helpers.escapeHtml(row.os_name)}` : '';
          return `${osType}${osName}`;
        }
      },
      { data: 'current_score' },
      {
        data: 'score_band',
        render: (value, type, row, helpers) => `<span class="${bandClass(value)}">${helpers.escapeHtml(value || '')}</span>`
      },
      { data: 'posture_status' },
      {
        data: null,
        orderable: false,
        render: () => `<button type="button" class="secondary compact-btn" data-act="view-detail">View detail</button>`
      }
    ]
  });

  async function loadDeviceDetail(deviceRow) {
    const deviceId = String(pick(deviceRow, 'device_external_id', 'deviceExternalId') || '').trim();
    if (!deviceId) return;
    const { effectiveTenant, headers } = resolveDetailAccess(deviceRow);

    state.selectedDeviceId = deviceId;
    state.selectedTenantId = effectiveTenant || null;
    state.selectedRowData = deviceRow;
    setSelectedDeviceUrl(deviceId, effectiveTenant);
    if (detailElements.refreshButton) {
      detailElements.refreshButton.disabled = false;
    }

    safeSetTextContent(detailElements.caption, `Device ${deviceId}${effectiveTenant ? ` (tenant ${effectiveTenant})` : ''}`);
    detailElements.error.hidden = true;
    detailElements.loading.hidden = false;
    detailElements.content.hidden = true;
    
    // Hide timeline section initially
    const timelineSection = document.getElementById('device-timeline-section');
    if (timelineSection) {
      timelineSection.hidden = true;
    }

    Object.keys(historyState).forEach((key) => {
      historyState[key].page = 0;
      historyState[key].hasMore = false;
    });

    const primaryResults = await Promise.allSettled([
      apiFetch(`/v1/devices/trust-profiles?device_external_id=${encodeURIComponent(deviceId)}&size=1`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/snapshots/latest`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/decisions?page=0&size=${HISTORY_PAGE_SIZE}`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/posture-payloads?page=0&size=${HISTORY_PAGE_SIZE}`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/trust-score-events?page=0&size=${HISTORY_PAGE_SIZE}`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/installed-apps?page=0&size=${HISTORY_PAGE_SIZE}`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/evaluation-runs?page=0&size=${HISTORY_PAGE_SIZE}`, { headers })
    ]);

    // Check for errors in critical requests
    const profileResult = primaryResults[0];
    if (profileResult.status === 'rejected') {
      console.error('Failed to load device profile:', profileResult.reason);
      safeSetTextContent(detailElements.error, `Failed to load device profile: ${profileResult.reason?.message || 'Unknown error'}`);
      detailElements.error.hidden = false;
      detailElements.loading.hidden = true;
      return;
    }

    const profilePayload = settledArray(primaryResults[0]);
    const profile = profilePayload[0] || null;
    
    // If profile doesn't exist, show error
    if (!profile) {
      safeSetTextContent(detailElements.error, `Device "${deviceId}" not found`);
      detailElements.error.hidden = false;
      detailElements.loading.hidden = true;
      return;
    }
    
    const snapshot = settledObject(primaryResults[1]);
    const decisions = settledArray(primaryResults[2]);
    const payloads = settledArray(primaryResults[3]);
    const events = settledArray(primaryResults[4]);
    
    // Handle installed apps - may fail if device has no apps or tenant mismatch
    let installedApps = [];
    if (primaryResults[5].status === 'fulfilled') {
      installedApps = settledArray(primaryResults[5]);
    } else {
      console.warn('Failed to load installed apps:', primaryResults[5].reason);
      // Continue loading other data, apps table will show "No rows"
    }
    
    const evaluationRuns = settledArray(primaryResults[6]);

    const latestPayload = payloads[0] || null;
    const latestRun = evaluationRuns[0] || null;

    let latestRunMatches = [];
    let latestRunRemediations = [];
    let latestRunDecisionResponse = null;

    const latestRunId = pick(latestRun, 'id');
    if (latestRunId) {
      const runResults = await Promise.allSettled([
        apiFetch(`/v1/evaluations/runs/${encodeURIComponent(latestRunId)}/matches`, { headers }),
        apiFetch(`/v1/evaluations/runs/${encodeURIComponent(latestRunId)}/remediations`, { headers }),
        apiFetch(`/v1/evaluations/runs/${encodeURIComponent(latestRunId)}/decision-response`, { headers })
      ]);
      latestRunMatches = settledArray(runResults[0]);
      latestRunRemediations = settledArray(runResults[1]);
      latestRunDecisionResponse = settledObject(runResults[2]);
    }

    const latestDecision = decisions[0] || latestRunDecisionResponse || null;

    if (!profile && !snapshot && !decisions.length && !payloads.length && !events.length && !installedApps.length && !evaluationRuns.length) {
      const messages = primaryResults
        .filter((result) => result.status === 'rejected')
        .map((result) => result.reason?.message || 'Request failed');
      safeSetTextContent(detailElements.error, messages[0] || 'No device detail available.');
      detailElements.error.hidden = false;
      detailElements.loading.hidden = true;
      detailElements.content.hidden = true;
      return;
    }

    if (detailElements.summary) {
      detailElements.summary.innerHTML = `
      <div class="stat">
        <div class="stat-label">Posture status</div>
        <div class="stat-value">${esc(textOrDash(pick(profile, 'posture_status', 'postureStatus') || pick(deviceRow, 'posture_status', 'postureStatus')))}</div>
      </div>
      <div class="stat">
        <div class="stat-label">Trust score</div>
        <div class="stat-value">${esc(textOrDash(pick(profile, 'current_score', 'currentScore') ?? pick(deviceRow, 'current_score', 'currentScore')))}</div>
      </div>
      <div class="stat">
        <div class="stat-label">Score band</div>
        <div class="stat-value">${renderBandBadge(pick(profile, 'score_band', 'scoreBand') || pick(deviceRow, 'score_band', 'scoreBand'))}</div>
      </div>
      <div class="stat">
        <div class="stat-label">Last decision</div>
        <div class="stat-value">${esc(textOrDash(pick(latestDecision, 'decision_action', 'decisionAction')))}</div>
      </div>
      <div class="stat">
        <div class="stat-label">Evaluation runs</div>
        <div class="stat-value">${esc(String(evaluationRuns.length))}</div>
      </div>
      <div class="stat">
        <div class="stat-label">Latest run ID</div>
        <div class="stat-value">${esc(textOrDash(pick(latestRun, 'id')))}</div>
      </div>
      <div class="stat">
        <div class="stat-label">Latest run matches</div>
        <div class="stat-value">${esc(String(latestRunMatches.length))}</div>
      </div>
      <div class="stat">
        <div class="stat-label">Latest run remediations</div>
        <div class="stat-value">${esc(String(latestRunRemediations.length))}</div>
      </div>
    `;
    }

    renderKeyValues(detailElements.profileMeta, [
      { label: 'Profile ID', value: pick(profile, 'id') },
      { label: 'Tenant', value: pick(profile, 'tenant_id', 'tenantId') || effectiveTenant },
      { label: 'Device ID', value: pick(profile, 'device_external_id', 'deviceExternalId') || deviceId },
      { label: 'Device type', value: pick(profile, 'device_type', 'deviceType') },
      { label: 'OS type', value: pick(profile, 'os_type', 'osType') || pick(deviceRow, 'os_type', 'osType') },
      { label: 'OS name', value: pick(profile, 'os_name', 'osName') || pick(deviceRow, 'os_name', 'osName') },
      { label: 'Lifecycle state', value: pick(profile, 'os_lifecycle_state', 'osLifecycleState') },
      { label: 'Lifecycle master ID', value: pick(profile, 'os_release_lifecycle_master_id', 'osReleaseLifecycleMasterId') },
      { label: 'Current score', value: pick(profile, 'current_score', 'currentScore') ?? pick(deviceRow, 'current_score', 'currentScore') },
      { label: 'Score band', value: pick(profile, 'score_band', 'scoreBand') || pick(deviceRow, 'score_band', 'scoreBand') },
      { label: 'Posture status', value: pick(profile, 'posture_status', 'postureStatus') || pick(deviceRow, 'posture_status', 'postureStatus') },
      { label: 'Last event', value: fmtDate(pick(profile, 'last_event_at', 'lastEventAt')) },
      { label: 'Last recalculated', value: fmtDate(pick(profile, 'last_recalculated_at', 'lastRecalculatedAt')) },
      { label: 'Modified at', value: fmtDate(pick(profile, 'modified_at', 'modifiedAt')) },
      { label: 'Modified by', value: pick(profile, 'modified_by', 'modifiedBy') }
    ]);

    renderKeyValues(detailElements.payloadMeta, [
      { label: 'Payload ID', value: pick(latestPayload, 'id') },
      { label: 'Agent ID', value: pick(latestPayload, 'agent_id', 'agentId') },
      { label: 'Version', value: pick(latestPayload, 'payload_version', 'payloadVersion') },
      { label: 'Hash', value: pick(latestPayload, 'payload_hash', 'payloadHash') },
      { label: 'Process status', value: pick(latestPayload, 'process_status', 'processStatus') },
      { label: 'Received at', value: fmtDate(pick(latestPayload, 'received_at', 'receivedAt')) },
      { label: 'Processed at', value: fmtDate(pick(latestPayload, 'processed_at', 'processedAt')) },
      { label: 'Created at', value: fmtDate(pick(latestPayload, 'created_at', 'createdAt')) },
      { label: 'Created by', value: pick(latestPayload, 'created_by', 'createdBy') },
      { label: 'Process error', value: pick(latestPayload, 'process_error', 'processError') }
    ]);

    renderKeyValues(detailElements.snapshot, [
      { label: 'Snapshot ID', value: pick(snapshot, 'id') },
      { label: 'Payload ID', value: pick(snapshot, 'device_posture_payload_id', 'devicePosturePayloadId') },
      { label: 'Profile ID', value: pick(snapshot, 'device_trust_profile_id', 'deviceTrustProfileId') },
      { label: 'Capture time', value: fmtDate(pick(snapshot, 'capture_time', 'captureTime')) },
      { label: 'Device type', value: pick(snapshot, 'device_type', 'deviceType') || pick(profile, 'device_type', 'deviceType') },
      { label: 'OS type', value: pick(snapshot, 'os_type', 'osType') || pick(profile, 'os_type', 'osType') || pick(deviceRow, 'os_type', 'osType') },
      { label: 'OS name', value: pick(snapshot, 'os_name', 'osName') || pick(profile, 'os_name', 'osName') || pick(deviceRow, 'os_name', 'osName') },
      { label: 'OS cycle', value: pick(snapshot, 'os_cycle', 'osCycle') },
      { label: 'OS version', value: pick(snapshot, 'os_version', 'osVersion') },
      { label: 'Lifecycle master ID', value: pick(snapshot, 'os_release_lifecycle_master_id', 'osReleaseLifecycleMasterId') },
      { label: 'API level', value: pick(snapshot, 'api_level', 'apiLevel') },
      { label: 'Build number', value: pick(snapshot, 'os_build_number', 'osBuildNumber') },
      { label: 'Kernel version', value: pick(snapshot, 'kernel_version', 'kernelVersion') },
      { label: 'Timezone', value: pick(snapshot, 'time_zone', 'timeZone') },
      { label: 'Manufacturer', value: pick(snapshot, 'manufacturer') },
      { label: 'Root detected', value: boolText(pick(snapshot, 'root_detected', 'rootDetected')) },
      { label: 'Emulator', value: boolText(pick(snapshot, 'running_on_emulator', 'runningOnEmulator')) },
      { label: 'USB debugging', value: boolText(pick(snapshot, 'usb_debugging_status', 'usbDebuggingStatus')) },
      { label: 'Is latest', value: boolText(pick(snapshot, 'is_latest', 'latest')) }
    ]);

    renderKeyValues(detailElements.decision, [
      { label: 'Decision ID', value: pick(latestDecision, 'id') },
      { label: 'Run ID', value: pick(latestDecision, 'posture_evaluation_run_id', 'postureEvaluationRunId') },
      { label: 'Action', value: pick(latestDecision, 'decision_action', 'decisionAction') },
      { label: 'Trust score', value: pick(latestDecision, 'trust_score', 'trustScore') },
      { label: 'Remediation required', value: boolText(pick(latestDecision, 'remediation_required', 'remediationRequired')) },
      { label: 'Delivery status', value: pick(latestDecision, 'delivery_status', 'deliveryStatus') },
      { label: 'Sent at', value: fmtDate(pick(latestDecision, 'sent_at', 'sentAt')) },
      { label: 'Acknowledged at', value: fmtDate(pick(latestDecision, 'acknowledged_at', 'acknowledgedAt')) },
      { label: 'Error', value: pick(latestDecision, 'error_message', 'errorMessage') },
      { label: 'Created at', value: fmtDate(pick(latestDecision, 'created_at', 'createdAt')) },
      { label: 'Created by', value: pick(latestDecision, 'created_by', 'createdBy') }
    ]);

    applyHistoryRows('apps', installedApps, 0);
    applyHistoryRows('decisions', decisions, 0);
    applyHistoryRows('payloads', payloads, 0);
    applyHistoryRows('events', events, 0);
    applyHistoryRows('runs', evaluationRuns, 0);

    renderTableBody(
      detailElements.runMatchesBody,
      latestRunMatches,
      [
        (row) => pick(row, 'id'),
        (row) => pick(row, 'match_source', 'matchSource'),
        (row) => pick(row, 'severity'),
        (row) => pick(row, 'compliance_action', 'complianceAction'),
        (row) => pick(row, 'score_delta', 'scoreDelta'),
        (row) => pick(row, 'os_lifecycle_state', 'osLifecycleState'),
        (row) => clip(pick(row, 'match_detail', 'matchDetail'), 100)
      ],
      7
    );

    renderTableBody(
      detailElements.runRemediationsBody,
      latestRunRemediations,
      [
        (row) => pick(row, 'id'),
        (row) => pick(row, 'remediation_rule_id', 'remediationRuleId'),
        (row) => pick(row, 'posture_evaluation_match_id', 'postureEvaluationMatchId'),
        (row) => pick(row, 'source_type', 'sourceType'),
        (row) => pick(row, 'remediation_status', 'remediationStatus'),
        (row) => fmtDate(pick(row, 'due_at', 'dueAt')),
        (row) => fmtDate(pick(row, 'completed_at', 'completedAt'))
      ],
      7
    );

    safeSetTextContent(detailElements.rawPayload, prettyJson(pick(latestPayload, 'payload_json', 'payloadJson')));
    safeSetTextContent(detailElements.rawDecision, prettyJson(pick(latestDecision, 'response_payload', 'responsePayload')));
    safeSetTextContent(detailElements.rawRunResponse, prettyJson(pick(latestRun, 'response_payload', 'responsePayload')));

    // Load device timeline
    loadDeviceTimeline(deviceRow);

    detailElements.loading.hidden = true;
    detailElements.content.hidden = false;
  }

  /**
   * Load device history timeline
   */
  async function loadDeviceTimeline(deviceRow) {
    const deviceId = String(pick(deviceRow, 'device_external_id', 'deviceExternalId') || '').trim();
    if (!deviceId) return;

    const { headers } = resolveDetailAccess(deviceRow);

    const timelineSection = document.getElementById('device-timeline-section');
    const timelineContainer = document.getElementById('device-timeline-container');
    
    if (!timelineSection || !timelineContainer) return;

    try {
      // Show timeline section
      timelineSection.hidden = false;
      
      // Fetch timeline data
      const profileId = deviceRow.id || deviceRow.device_trust_profile_id;
      if (!profileId) {
        timelineContainer.innerHTML = '<div class="timeline-empty"><p>Device profile ID not available</p></div>';
        return;
      }

      const response = await apiFetch(`/v1/devices/${profileId}/timeline?limit=50`, { headers });
      
      // Initialize timeline viewer if not already done
      if (!window.deviceTimelineViewer) {
        window.deviceTimelineViewer = initTimelineViewer('device-timeline-container');
      }
      
      // Render timeline
      window.deviceTimelineViewer.container.dataset.profileId = profileId;
      const { renderTimeline } = await import('./device-timeline.js');
      renderTimeline(timelineContainer, response);
      
    } catch (error) {
      console.error('Failed to load timeline:', error);
      if (timelineContainer) {
        timelineContainer.innerHTML = `
          <div class="timeline-error">
            <div class="error-icon">Error</div>
            <p>Failed to load timeline</p>
            <p class="muted">${error.message}</p>
          </div>
        `;
      }
      if (timelineSection) {
        timelineSection.hidden = false;
      }
    }
  }

  function safeSetTextContent(element, text) {
    if (element) {
      element.textContent = text;
    }
  }

  async function maybeLoadDeviceFromQuery() {
    const params = new URLSearchParams(window.location.search);
    const deviceId = String(params.get('device_external_id') || '').trim();
    if (!deviceId) {
      return;
    }
    const tenantId = String(params.get('tenant_id') || '').trim();
    if (detailElements.tenantOverride && tenantId) {
      detailElements.tenantOverride.value = tenantId;
    }
    try {
      const headers = tenantId ? { 'X-Tenant-Id': tenantId } : {};
      const profiles = await apiFetch(`/v1/devices/trust-profiles?device_external_id=${encodeURIComponent(deviceId)}&size=1`, { headers });
      const row = Array.isArray(profiles) ? (profiles[0] || null) : profiles;
      if (!row) {
        throw new Error(`Device "${deviceId}" not found`);
      }
      await loadDeviceDetail(row);
    } catch (error) {
      detailElements.loading.hidden = true;
      detailElements.content.hidden = true;
      safeSetTextContent(detailElements.error, `Failed to open device detail: ${error.message}`);
      detailElements.error.hidden = false;
    }
  }

  const tableBody = document.querySelector('#device-table tbody');
  tableBody?.addEventListener('click', async (event) => {
    const actionBtn = event.target.closest('button[data-act="view-detail"]');
    if (!actionBtn) return;

    const rowEl = actionBtn.closest('tr[data-index]');
    if (!rowEl) return;

    const rowData = dt.row(rowEl).data();
    if (!rowData) return;

    if (state.selectedRowEl && state.selectedRowEl !== rowEl) {
      state.selectedRowEl.classList.remove('is-selected');
    }
    rowEl.classList.add('is-selected');
    state.selectedRowEl = rowEl;

    try {
      await loadDeviceDetail(rowData);
    } catch (error) {
      detailElements.loading.hidden = true;
      detailElements.content.hidden = true;
      safeSetTextContent(detailElements.error, `Failed to load device detail: ${error.message}`);
      detailElements.error.hidden = false;
    }
  });

  document.getElementById('device-filter-form')?.addEventListener('submit', (e) => {
    e.preventDefault();
    dt.state.start = 0;
    dt.ajax.reload();
    if (state.selectedRowEl) {
      state.selectedRowEl.classList.remove('is-selected');
      state.selectedRowEl = null;
    }
    state.selectedDeviceId = null;
    state.selectedTenantId = null;
    state.selectedRowData = null;
    setSelectedDeviceUrl(null, null);
    resetHistorySections();
    safeSetTextContent(detailElements.caption, 'Click View detail on a device row to view latest posture status and historical posture information.');
    detailElements.error.hidden = true;
    detailElements.loading.hidden = true;
    detailElements.content.hidden = true;
    safeSetTextContent(detailElements.rawPayload, 'No data.');
    safeSetTextContent(detailElements.rawDecision, 'No data.');
    safeSetTextContent(detailElements.rawRunResponse, 'No data.');
    if (detailElements.refreshButton) {
      detailElements.refreshButton.disabled = true;
    }
  });

  document.getElementById('device-detail-content')?.addEventListener('click', async (event) => {
    const navButton = event.target.closest('button[data-history-nav][data-direction]');
    if (!navButton || !state.selectedDeviceId) {
      return;
    }
    const key = navButton.getAttribute('data-history-nav');
    const direction = navButton.getAttribute('data-direction');
    if (!key || !historyState[key]) {
      return;
    }
    const nextPage = direction === 'next'
      ? historyState[key].page + 1
      : Math.max(historyState[key].page - 1, 0);
    if (nextPage === historyState[key].page) {
      return;
    }
    try {
      await loadHistoryPage(key, nextPage);
    } catch (error) {
      console.error(`Failed to load ${key} history:`, error);
      setHistoryError(key, error.message);
    }
  });

  document.getElementById('device-detail-controls')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!state.selectedDeviceId) return;
    try {
      await loadDeviceDetail(state.selectedRowData || {
        device_external_id: state.selectedDeviceId,
        tenant_id: state.selectedTenantId || ''
      });
    } catch (error) {
      detailElements.loading.hidden = true;
      detailElements.content.hidden = true;
      safeSetTextContent(detailElements.error, `Failed to refresh device detail: ${error.message}`);
      detailElements.error.hidden = false;
    }
  });
  
  // Refresh timeline button
  document.getElementById('refresh-timeline')?.addEventListener('click', async () => {
    if (state.selectedRowData) {
      try {
        await loadDeviceTimeline(state.selectedRowData);
      } catch (error) {
        console.error('Failed to refresh timeline:', error);
      }
    }
  });

  resetHistorySections();
  await maybeLoadDeviceFromQuery();
});
