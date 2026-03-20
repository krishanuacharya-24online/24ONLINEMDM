import { apiFetch } from '../api.js';

function byId(id) {
  return document.getElementById(id);
}

function cfg(config, camelKey, snakeKey) {
  if (!config) return null;
  const camelValue = config[camelKey];
  if (camelValue !== undefined && camelValue !== null) return camelValue;
  const snakeValue = config[snakeKey];
  return snakeValue !== undefined && snakeValue !== null ? snakeValue : null;
}

function setRemediationStatus(message, isError = false) {
  const status = byId('remediationReportStatus');
  if (!status) return;
  const text = String(message || '').trim();
  status.textContent = text;
  status.hidden = text.length === 0;
  status.classList.toggle('error', Boolean(isError));
}

function setFleetStatus(message, isError = false) {
  const status = byId('fleetReportStatus');
  if (!status) return;
  const text = String(message || '').trim();
  status.textContent = text;
  status.hidden = text.length === 0;
  status.classList.toggle('error', Boolean(isError));
}

function toNumber(value) {
  const num = Number(value ?? 0);
  return Number.isFinite(num) ? num : 0;
}

function formatDateTime(value) {
  if (!value) return '---';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '---';
  return date.toLocaleString();
}

function formatDateOnly(value) {
  if (!value) return '---';
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const date = new Date(`${value}T00:00:00Z`);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString();
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleDateString();
}

function formatDecimal(value, digits = 1) {
  const num = Number(value ?? 0);
  return Number.isFinite(num) ? num.toFixed(digits) : (0).toFixed(digits);
}

function formatAge(value) {
  if (!value) return '---';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '---';
  const diffMs = Date.now() - date.getTime();
  if (diffMs < 60000) return 'just now';
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

function esc(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function buildDeviceDetailHref(deviceId, tenantId) {
  const normalizedDeviceId = String(deviceId || '').trim();
  if (!normalizedDeviceId) {
    return '/ui/devices';
  }
  const params = new URLSearchParams();
  params.set('device_external_id', normalizedDeviceId);
  const normalizedTenantId = String(tenantId || '').trim();
  if (normalizedTenantId) {
    params.set('tenant_id', normalizedTenantId);
  }
  return `/ui/devices?${params.toString()}`;
}

function renderOpenDeviceLink(row) {
  const deviceId = row?.device_external_id || '';
  if (!String(deviceId).trim()) {
    return '<span class="muted">---</span>';
  }
  return `<a class="table-action-link" href="${esc(buildDeviceDetailHref(deviceId, row?.tenant_id || ''))}">Open</a>`;
}

function remediationBadge(status) {
  const normalized = String(status || '').trim().toUpperCase();
  const badgeClass = (() => {
    switch (normalized) {
      case 'RESOLVED_ON_RESCAN':
      case 'CLOSED':
        return 'badge badge--trusted';
      case 'USER_ACKNOWLEDGED':
        return 'badge badge--ok';
      case 'STILL_OPEN':
        return 'badge badge--high-risk';
      case 'DELIVERED':
        return 'badge badge--warn';
      case 'PROPOSED':
        return 'badge badge--danger';
      default:
        return 'badge';
    }
  })();
  return `<span class="${badgeClass}">${esc(normalized || 'UNKNOWN')}</span>`;
}

function scoreBandBadge(status) {
  const normalized = String(status || '').trim().toUpperCase();
  const badgeClass = (() => {
    switch (normalized) {
      case 'TRUSTED':
        return 'badge badge--trusted';
      case 'LOW_RISK':
        return 'badge badge--low-risk';
      case 'MEDIUM_RISK':
        return 'badge badge--medium-risk';
      case 'HIGH_RISK':
        return 'badge badge--high-risk';
      case 'CRITICAL':
        return 'badge badge--critical';
      default:
        return 'badge';
    }
  })();
  return `<span class="${badgeClass}">${esc(normalized || 'UNKNOWN')}</span>`;
}

function compatibilityBadge(status) {
  const normalized = String(status || '').trim().toUpperCase();
  const badgeClass = (() => {
    switch (normalized) {
      case 'SUPPORTED':
        return 'badge badge--ok';
      case 'SUPPORTED_WITH_WARNINGS':
        return 'badge badge--warn';
      case 'UNSUPPORTED':
        return 'badge badge--danger';
      default:
        return 'badge';
    }
  })();
  return `<span class="${badgeClass}">${esc(normalized || 'UNVERIFIED')}</span>`;
}

function renderRemediationSummary(summary) {
  const panel = byId('remediationSummaryPanel');
  if (!panel) return;

  byId('remediationTrackedIssues').textContent = String(toNumber(cfg(summary, 'totalTrackedIssues', 'total_tracked_issues')));
  byId('remediationOpenIssues').textContent = String(toNumber(cfg(summary, 'openIssues', 'open_issues')));
  byId('remediationResolvedIssues').textContent = String(toNumber(cfg(summary, 'resolvedIssues', 'resolved_issues')));
  byId('remediationDevicesOpen').textContent = String(toNumber(cfg(summary, 'devicesWithOpenIssues', 'devices_with_open_issues')));
  byId('remediationAwaitingVerification').textContent = String(toNumber(cfg(summary, 'awaitingVerificationIssues', 'awaiting_verification_issues')));
  byId('remediationStillOpen').textContent = String(toNumber(cfg(summary, 'stillOpenIssues', 'still_open_issues')));
  byId('remediationResolvedOnRescan').textContent = String(toNumber(cfg(summary, 'resolvedOnRescanIssues', 'resolved_on_rescan_issues')));

  const scopeTenantId = String(cfg(summary, 'scopeTenantId', 'scope_tenant_id') || '').trim();
  byId('remediationScope').textContent = scopeTenantId ? `Tenant: ${scopeTenantId}` : 'All tenants';
  byId('remediationLatestResolved').textContent = formatDateTime(cfg(summary, 'latestResolvedAt', 'latest_resolved_at'));

  panel.hidden = false;
  setRemediationStatus('');
}

function queryReport(path, params = {}) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null) return;
    const text = String(value).trim();
    if (!text) return;
    query.set(key, text);
  });
  const suffix = query.size ? `?${query.toString()}` : '';
  return apiFetch(`${path}${suffix}`);
}

function renderFleetSummary(summary) {
  const panel = byId('fleetVisibilityPanel');
  if (!panel) return;

  byId('fleetTrackedDevices').textContent = String(toNumber(cfg(summary, 'totalDevices', 'total_devices')));
  byId('fleetStaleDevices').textContent = String(toNumber(cfg(summary, 'staleDevices', 'stale_devices')));
  byId('fleetHighRiskDevices').textContent = String(toNumber(cfg(summary, 'highRiskDevices', 'high_risk_devices')));
  byId('fleetLifecycleRiskDevices').textContent = String(toNumber(cfg(summary, 'lifecycleRiskDevices', 'lifecycle_risk_devices')));
  byId('fleetCriticalDevices').textContent = String(toNumber(cfg(summary, 'criticalDevices', 'critical_devices')));
  byId('fleetSupportedDevices').textContent = String(toNumber(cfg(summary, 'supportedDevices', 'supported_devices')));
  byId('fleetEolDevices').textContent = String(toNumber(cfg(summary, 'eolDevices', 'eol_devices')));
  byId('fleetEeolDevices').textContent = String(toNumber(cfg(summary, 'eeolDevices', 'eeol_devices')));
  byId('fleetNotTrackedDevices').textContent = String(toNumber(cfg(summary, 'notTrackedDevices', 'not_tracked_devices')));
  byId('fleetStaleThreshold').textContent = `${toNumber(cfg(summary, 'staleAfterHours', 'stale_after_hours'))} hours`;

  panel.hidden = false;
  setFleetStatus('');
}

function renderTopFailingRules(rows) {
  const body = byId('topFailingRulesBody');
  if (!body) return;
  if (!Array.isArray(rows) || rows.length === 0) {
    body.innerHTML = '<tr><td colspan="6" class="muted">No current failing system rules.</td></tr>';
    return;
  }

  body.innerHTML = rows.map((row) => {
    const code = cfg(row, 'ruleCode', 'rule_code') || '---';
    const description = cfg(row, 'ruleDescription', 'rule_description') || '';
    const tag = cfg(row, 'ruleTag', 'rule_tag') || '---';
    const action = cfg(row, 'complianceAction', 'compliance_action') || 'ALLOW';
    return `
      <tr>
        <td>${description ? `<strong>${esc(code)}</strong><br><small>${esc(description)}</small>` : esc(code)}</td>
        <td>${esc(tag)}</td>
        <td>${esc(action)}</td>
        <td>${esc(toNumber(cfg(row, 'impactedDevices', 'impacted_devices')))}</td>
        <td>${esc(toNumber(cfg(row, 'blockedDevices', 'blocked_devices')))}</td>
        <td>${esc(formatDateTime(cfg(row, 'latestEvaluatedAt', 'latest_evaluated_at')))}</td>
      </tr>
    `;
  }).join('');
}

function renderTopRiskyApplications(rows) {
  const body = byId('topRiskyApplicationsBody');
  if (!body) return;
  if (!Array.isArray(rows) || rows.length === 0) {
    body.innerHTML = '<tr><td colspan="5" class="muted">No current risky applications.</td></tr>';
    return;
  }

  body.innerHTML = rows.map((row) => {
    const appName = cfg(row, 'appName', 'app_name') || 'Unknown application';
    const packageId = cfg(row, 'packageId', 'package_id') || '';
    const publisher = cfg(row, 'publisher', 'publisher') || '';
    const policyTag = cfg(row, 'policyTag', 'policy_tag') || 'REJECT_APP';
    return `
      <tr>
        <td>${packageId || publisher ? `<strong>${esc(appName)}</strong><br><small>${esc(packageId || publisher)}</small>` : esc(appName)}</td>
        <td>${esc(policyTag)}</td>
        <td>${esc(toNumber(cfg(row, 'impactedDevices', 'impacted_devices')))}</td>
        <td>${esc(toNumber(cfg(row, 'blockedDevices', 'blocked_devices')))}</td>
        <td>${esc(formatDateTime(cfg(row, 'latestEvaluatedAt', 'latest_evaluated_at')))}</td>
      </tr>
    `;
  }).join('');
}

function renderScoreTrend(rows) {
  const body = byId('fleetScoreTrendBody');
  if (!body) return;
  if (!Array.isArray(rows) || rows.length === 0) {
    body.innerHTML = '<tr><td colspan="8" class="muted">No score-trend data for the selected window.</td></tr>';
    return;
  }

  body.innerHTML = rows.map((row) => `
    <tr>
      <td>${esc(formatDateOnly(cfg(row, 'bucketDate', 'bucket_date')))}</td>
      <td>${esc(toNumber(cfg(row, 'evaluationCount', 'evaluation_count')))}</td>
      <td>${esc(toNumber(cfg(row, 'distinctDevices', 'distinct_devices')))}</td>
      <td>${esc(formatDecimal(cfg(row, 'averageTrustScore', 'average_trust_score')))}</td>
      <td>${esc(toNumber(cfg(row, 'allowCount', 'allow_count')))}</td>
      <td>${esc(toNumber(cfg(row, 'notifyCount', 'notify_count')))}</td>
      <td>${esc(toNumber(cfg(row, 'quarantineCount', 'quarantine_count')))}</td>
      <td>${esc(toNumber(cfg(row, 'blockCount', 'block_count')))}</td>
    </tr>
  `).join('');
}

function renderAgentVersionDistribution(rows) {
  const body = byId('agentVersionDistributionBody');
  if (!body) return;
  if (!Array.isArray(rows) || rows.length === 0) {
    body.innerHTML = '<tr><td colspan="5" class="muted">No current agent-version telemetry.</td></tr>';
    return;
  }

  body.innerHTML = rows.map((row) => `
    <tr>
      <td>${esc(cfg(row, 'agentVersion', 'agent_version') || 'UNKNOWN')}</td>
      <td>${compatibilityBadge(cfg(row, 'schemaCompatibilityStatus', 'schema_compatibility_status'))}</td>
      <td>${esc(toNumber(cfg(row, 'deviceCount', 'device_count')))}</td>
      <td>${esc(toNumber(cfg(row, 'devicesWithCapabilities', 'devices_with_capabilities')))}</td>
      <td>${esc(formatDateTime(cfg(row, 'latestCaptureTime', 'latest_capture_time')))}</td>
    </tr>
  `).join('');
}

function renderAgentCapabilityCoverage(rows) {
  const body = byId('agentCapabilityCoverageBody');
  if (!body) return;
  if (!Array.isArray(rows) || rows.length === 0) {
    body.innerHTML = '<tr><td colspan="3" class="muted">No current agent capabilities reported.</td></tr>';
    return;
  }

  body.innerHTML = rows.map((row) => `
    <tr>
      <td>${esc(cfg(row, 'capabilityKey', 'capability_key') || 'UNKNOWN')}</td>
      <td>${esc(toNumber(cfg(row, 'deviceCount', 'device_count')))}</td>
      <td>${esc(formatDateTime(cfg(row, 'latestCaptureTime', 'latest_capture_time')))}</td>
    </tr>
  `).join('');
}

let remediationTable = null;
let staleDevicesTable = null;

function initStaleDevicesTable() {
  if (staleDevicesTable) {
    return staleDevicesTable;
  }

  staleDevicesTable = window.mdmInitDataTable('#staleDevicesTable', {
    ajax: { url: '/v1/reports/fleet/stale-devices/table' },
    pageLength: 25,
    defaultSortBy: 'latest_seen_at',
    defaultSortDir: 'asc',
    extraParams: () => ({
      stale_after_hours: byId('fleetStaleAfterHours')?.value || '72'
    }),
    columns: [
      { data: 'id' },
      { data: 'tenant_id' },
      { data: 'device_external_id' },
      {
        data: 'latest_seen_at',
        render: (value) => formatDateTime(value)
      },
      {
        data: 'latest_seen_at',
        orderable: false,
        render: (value) => formatAge(value)
      },
      {
        data: 'score_band',
        render: (value) => scoreBandBadge(value)
      },
      { data: 'current_score' },
      { data: 'posture_status' },
      { data: 'os_lifecycle_state' },
      {
        data: 'os_type',
        render: (_, __, row) => {
          const osType = row.os_type || '---';
          const osName = row.os_name ? ` / ${row.os_name}` : '';
          return `${esc(osType)}${esc(osName)}`;
        }
      },
      {
        data: null,
        orderable: false,
        render: (_, __, row) => renderOpenDeviceLink(row)
      }
    ]
  });

  return staleDevicesTable;
}

function initRemediationTable() {
  if (remediationTable) {
    return remediationTable;
  }

  remediationTable = window.mdmInitDataTable('#remediationReportTable', {
    ajax: { url: '/v1/reports/remediation/table' },
    pageLength: 25,
    defaultSortBy: 'status_updated_at',
    defaultSortDir: 'desc',
    extraParams: () => ({
      status_view: byId('remediationStatusView')?.value || 'ALL'
    }),
    columns: [
      { data: 'id' },
      { data: 'tenant_id' },
      { data: 'device_external_id' },
      {
        data: 'remediation_code',
        render: (_, __, row) => {
          const code = row.remediation_code || '---';
          const title = row.remediation_title || '';
          return title ? `<strong>${esc(code)}</strong><br><small>${esc(title)}</small>` : esc(code);
        }
      },
      {
        data: 'remediation_status',
        render: (value) => remediationBadge(value)
      },
      { data: 'decision_action' },
      {
        data: 'source_type',
        render: (_, __, row) => {
          const source = row.source_type || '---';
          const match = row.match_source ? ` / ${row.match_source}` : '';
          return `${esc(source)}${esc(match)}`;
        }
      },
      {
        data: 'opened_at',
        render: (value) => formatDateTime(value)
      },
      {
        data: 'verified_at',
        render: (value) => formatDateTime(value)
      },
      {
        data: 'status_updated_at',
        render: (value) => formatDateTime(value)
      },
      {
        data: null,
        orderable: false,
        render: (_, __, row) => renderOpenDeviceLink(row)
      }
    ]
  });

  return remediationTable;
}

async function loadRemediationReport() {
  const summary = await apiFetch('/v1/reports/remediation/summary');
  renderRemediationSummary(summary);
  const table = initRemediationTable();
  await table.ajax.reload();
}

async function loadFleetReport() {
  const staleAfterHours = byId('fleetStaleAfterHours')?.value || '72';
  const trendDays = byId('fleetTrendDays')?.value || '14';
  const [summary, topRules, topApps, scoreTrend, agentVersions, agentCapabilities] = await Promise.all([
    queryReport('/v1/reports/fleet/summary', { stale_after_hours: staleAfterHours }),
    queryReport('/v1/reports/fleet/top-failing-rules', { limit: 8 }),
    queryReport('/v1/reports/fleet/top-risky-applications', { limit: 8 }),
    queryReport('/v1/reports/fleet/score-trend', { days: trendDays }),
    queryReport('/v1/reports/fleet/agent-versions', { limit: 8 }),
    queryReport('/v1/reports/fleet/agent-capabilities', { limit: 8 })
  ]);

  renderFleetSummary(summary);
  renderScoreTrend(scoreTrend);
  renderTopFailingRules(topRules);
  renderTopRiskyApplications(topApps);
  renderAgentVersionDistribution(agentVersions);
  renderAgentCapabilityCoverage(agentCapabilities);
  const table = initStaleDevicesTable();
  await table.ajax.reload();
}

document.addEventListener('DOMContentLoaded', async () => {
  try {
    await loadFleetReport();
  } catch (error) {
    setFleetStatus(`Failed to load fleet visibility: ${error.message}`, true);
  }

  byId('fleetStaleAfterHours')?.addEventListener('change', async () => {
    try {
      await loadFleetReport();
    } catch (error) {
      setFleetStatus(`Failed to refresh fleet visibility: ${error.message}`, true);
    }
  });

  byId('fleetTrendDays')?.addEventListener('change', async () => {
    try {
      await loadFleetReport();
    } catch (error) {
      setFleetStatus(`Failed to refresh fleet visibility: ${error.message}`, true);
    }
  });

  byId('refreshFleetReport')?.addEventListener('click', async () => {
    try {
      await loadFleetReport();
    } catch (error) {
      setFleetStatus(`Failed to refresh fleet visibility: ${error.message}`, true);
    }
  });

  try {
    await loadRemediationReport();
  } catch (error) {
    setRemediationStatus(`Failed to load remediation reports: ${error.message}`, true);
  }

  byId('remediationStatusView')?.addEventListener('change', async () => {
    if (!remediationTable) return;
    try {
      await remediationTable.ajax.reload();
    } catch (error) {
      setRemediationStatus(`Failed to refresh remediation table: ${error.message}`, true);
    }
  });

  byId('refreshRemediationReport')?.addEventListener('click', async () => {
    try {
      await loadRemediationReport();
    } catch (error) {
      setRemediationStatus(`Failed to refresh remediation reports: ${error.message}`, true);
    }
  });
});
