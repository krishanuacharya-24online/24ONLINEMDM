import { apiFetch } from '../api.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';
import { initTimelineViewer } from './device-timeline.js';

const OS_TYPE_OPTIONS = ['ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD'];
const SCORE_BAND_OPTIONS = ['TRUSTED', 'LOW_RISK', 'MEDIUM_RISK', 'HIGH_RISK', 'CRITICAL'];

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
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
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
  const text = String(value).trim();
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
  const text = String(value || '');
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
    const text = value.trim();
    if (!text) return 'No data.';
    try {
      return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
      return text;
    }
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return String(value);
    }
  }
  return String(value);
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
  container.innerHTML = pairs.map((pair) => `
    <div class="k">${esc(pair.label)}</div>
    <div class="v">${esc(textOrDash(pair.value))}</div>
  `).join('');
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

    const tenantFromRow = String(pick(deviceRow, 'tenant_id', 'tenantId') || '').trim();
    const tenantFromInput = (detailElements.tenantOverride?.value || '').trim().toLowerCase();
    const effectiveTenant = tenantFromInput || tenantFromRow;
    const headers = effectiveTenant ? { 'X-Tenant-Id': effectiveTenant } : {};

    state.selectedDeviceId = deviceId;
    state.selectedTenantId = tenantFromRow || null;
    state.selectedRowData = deviceRow;
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

    const primaryResults = await Promise.allSettled([
      apiFetch(`/v1/devices/trust-profiles?device_external_id=${encodeURIComponent(deviceId)}&size=1`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/snapshots/latest`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/decisions?size=20`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/posture-payloads?size=20`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/trust-score-events?size=20`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/installed-apps?size=20`, { headers }),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/evaluation-runs?size=20`, { headers })
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
      { label: 'Tenant', value: pick(profile, 'tenant_id', 'tenantId') || tenantFromRow || effectiveTenant },
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

    renderTableBody(
      detailElements.appsBody,
      installedApps,
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
    );

    renderTableBody(
      detailElements.decisionsBody,
      decisions,
      [
        (row) => pick(row, 'decision_action', 'decisionAction'),
        (row) => pick(row, 'trust_score', 'trustScore'),
        (row) => boolText(pick(row, 'remediation_required', 'remediationRequired')),
        (row) => pick(row, 'delivery_status', 'deliveryStatus'),
        (row) => fmtDate(pick(row, 'sent_at', 'sentAt')),
        (row) => fmtDate(pick(row, 'acknowledged_at', 'acknowledgedAt')),
        (row) => clip(pick(row, 'error_message', 'errorMessage'), 80),
        (row) => fmtDate(pick(row, 'created_at', 'createdAt'))
      ],
      8
    );

    renderTableBody(
      detailElements.payloadBody,
      payloads,
      [
        (row) => pick(row, 'id'),
        (row) => pick(row, 'agent_id', 'agentId'),
        (row) => pick(row, 'process_status', 'processStatus'),
        (row) => pick(row, 'payload_version', 'payloadVersion'),
        (row) => shortHash(pick(row, 'payload_hash', 'payloadHash')),
        (row) => fmtDate(pick(row, 'received_at', 'receivedAt')),
        (row) => fmtDate(pick(row, 'processed_at', 'processedAt')),
        (row) => clip(pick(row, 'process_error', 'processError'), 80)
      ],
      8
    );

    renderTableBody(
      detailElements.eventsBody,
      events,
      [
        (row) => pick(row, 'event_source', 'eventSource'),
        (row) => pick(row, 'score_before', 'scoreBefore'),
        (row) => pick(row, 'score_delta', 'scoreDelta'),
        (row) => pick(row, 'score_after', 'scoreAfter'),
        (row) => pick(row, 'os_lifecycle_state', 'osLifecycleState'),
        (row) => pick(row, 'source_record_id', 'sourceRecordId'),
        (row) => clip(pick(row, 'notes'), 80),
        (row) => fmtDate(pick(row, 'event_time', 'eventTime'))
      ],
      8
    );

    renderTableBody(
      detailElements.runsBody,
      evaluationRuns,
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
    );

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

    const tenantFromRow = String(pick(deviceRow, 'tenant_id', 'tenantId') || '').trim();
    const tenantFromInput = (detailElements.tenantOverride?.value || '').trim().toLowerCase();
    const effectiveTenant = tenantFromInput || tenantFromRow;
    const headers = effectiveTenant ? { 'X-Tenant-Id': effectiveTenant } : {};

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
            <div class="error-icon">❌</div>
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
    if (state.selectedRowData && window.deviceTimelineViewer) {
      try {
        const { renderTimeline } = await import('./device-timeline.js');
        const profileId = state.selectedRowData.id || state.selectedRowData.device_trust_profile_id;
        const response = await apiFetch(`/v1/devices/${profileId}/timeline?limit=50`);
        renderTimeline(document.getElementById('device-timeline-container'), response);
      } catch (error) {
        console.error('Failed to refresh timeline:', error);
      }
    }
  });
});
