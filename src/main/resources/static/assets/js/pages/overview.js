import { apiFetch } from '../api.js';

const SCORE_BANDS = ['TRUSTED', 'LOW_RISK', 'MEDIUM_RISK', 'HIGH_RISK', 'CRITICAL'];

function qs(id) {
  return document.getElementById(id);
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

function numberOrZero(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function textOrDash(value) {
  if (value === null || value === undefined) return '--';
  const text = String(value).trim();
  return text ? text : '--';
}

function boolText(value) {
  if (value === null || value === undefined) return '--';
  return value ? 'true' : 'false';
}

function fmtDate(value) {
  if (!value) return '--';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return textOrDash(value);
  return date.toLocaleString();
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

function fmtDateOnly(value) {
  if (!value) return '--';
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const date = new Date(`${value}T00:00:00Z`);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString();
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return textOrDash(value);
  return date.toLocaleDateString();
}

function fmtAge(value) {
  if (!value) return '--';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '--';
  const diffMs = Date.now() - date.getTime();
  if (diffMs < 0) return 'just now';
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

function fmtDurationMinutes(value) {
  const minutes = numberOrZero(value);
  if (!minutes) return '--';
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  const remMinutes = minutes % 60;
  if (!remMinutes) return `${hours}h`;
  return `${hours}h ${remMinutes}m`;
}

function pct(value, total) {
  if (!total) return '0%';
  return `${((value / total) * 100).toFixed(1)}%`;
}

function formatDecimal(value, digits = 1) {
  const n = Number(value);
  return Number.isFinite(n) ? n.toFixed(digits) : (0).toFixed(digits);
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

function renderKeyValues(container, pairs) {
  if (!container) return;
  container.innerHTML = pairs.map((pair) => `
    <div class="k">${esc(pair.label)}</div>
    <div class="v">${esc(textOrDash(pair.value))}</div>
  `).join('');
}

function tenantHeaders() {
  const tenantId = (qs('tenantId')?.value || '').trim();
  return tenantId ? { 'X-Tenant-Id': tenantId } : {};
}

function badgeClassForStatus(status) {
  const normalized = String(status || '').trim().toUpperCase();
  if (!normalized) return 'badge';
  if (normalized === 'TRUSTED') return 'badge badge--trusted';
  if (normalized === 'LOW_RISK') return 'badge badge--low-risk';
  if (normalized === 'MEDIUM_RISK') return 'badge badge--medium-risk';
  if (normalized === 'HIGH_RISK') return 'badge badge--high-risk';
  if (normalized === 'CRITICAL') return 'badge badge--critical';
  if (normalized.includes('FAIL') || normalized.includes('ERROR') || normalized.includes('REJECT')) {
    return 'badge badge--danger';
  }
  if (normalized.includes('PENDING') || normalized.includes('RECEIVED') || normalized.includes('QUEUED')) {
    return 'badge badge--warn';
  }
  return 'badge badge--ok';
}

function badgeClassForBand(band) {
  const normalized = String(band || '').trim().toUpperCase();
  if (normalized === 'TRUSTED') return 'badge badge--trusted';
  if (normalized === 'LOW_RISK') return 'badge badge--low-risk';
  if (normalized === 'MEDIUM_RISK') return 'badge badge--medium-risk';
  if (normalized === 'HIGH_RISK') return 'badge badge--high-risk';
  if (normalized === 'CRITICAL') return 'badge badge--critical';
  return 'badge';
}

function renderBandBadge(band) {
  const label = textOrDash(band);
  if (label === '--') return esc(label);
  return `<span class="${badgeClassForBand(label)}">${esc(label)}</span>`;
}

function barClassForBand(band) {
  switch (String(band || '').trim().toUpperCase()) {
    case 'TRUSTED':
      return 'overview-bar overview-bar--trusted';
    case 'LOW_RISK':
      return 'overview-bar overview-bar--low-risk';
    case 'MEDIUM_RISK':
      return 'overview-bar overview-bar--medium-risk';
    case 'HIGH_RISK':
      return 'overview-bar overview-bar--high-risk';
    case 'CRITICAL':
      return 'overview-bar overview-bar--critical';
    default:
      return 'overview-bar overview-bar--ok';
  }
}

async function queryDataTable(path, params = {}) {
  const merged = {
    draw: 1,
    start: 0,
    length: 25,
    ...params
  };
  const query = new URLSearchParams();
  Object.entries(merged).forEach(([key, value]) => {
    if (value === undefined || value === null) return;
    const text = String(value).trim();
    if (!text) return;
    query.set(key, text);
  });
  return apiFetch(`${path}?${query.toString()}`);
}

async function queryCount(path, filters = {}) {
  const payload = await queryDataTable(path, { ...filters, start: 0, length: 1 });
  return numberOrZero(payload?.recordsTotal ?? payload?.records_total ?? 0);
}

function readFleetBase() {
  return {
    total: numberOrZero(qs('fleetTotalDevices')?.dataset.value),
    trusted: numberOrZero(qs('fleetTrustedDevices')?.dataset.value),
    highRisk: numberOrZero(qs('fleetHighRiskDevices')?.dataset.value),
    openRemediations: numberOrZero(qs('fleetOpenRemediations')?.dataset.value)
  };
}

function renderDerivedFleetStats() {
  const node = qs('overviewDerivedStats');
  if (!node) return;
  const base = readFleetBase();
  const healthyPct = pct(base.trusted, base.total);
  const highRiskPct = pct(base.highRisk, base.total);
  const remediationPressure = base.total ? (base.openRemediations / base.total).toFixed(2) : '0.00';
  node.innerHTML = `
    <div class="stat">
      <div class="stat-label">Healthy ratio</div>
      <div class="stat-value">${esc(healthyPct)}</div>
    </div>
    <div class="stat">
      <div class="stat-label">High-risk ratio</div>
      <div class="stat-value">${esc(highRiskPct)}</div>
    </div>
    <div class="stat">
      <div class="stat-label">Remediation pressure</div>
      <div class="stat-value">${esc(remediationPressure)}</div>
    </div>
  `;
}

function updateHealthView(health, errorMessage) {
  const statusBadge = qs('overviewHealthStatus');
  const service = qs('overviewHealthService');
  const time = qs('overviewHealthTime');
  const refresh = qs('overviewHealthRefresh');

  if (refresh) {
    refresh.textContent = new Date().toLocaleString();
  }

  if (!health) {
    if (statusBadge) {
      statusBadge.className = 'badge badge--danger';
      statusBadge.textContent = 'DOWN';
    }
    if (service) {
      service.textContent = errorMessage ? `Unavailable (${errorMessage})` : 'Unavailable';
    }
    if (time) {
      time.textContent = '--';
    }
    return;
  }

  const statusText = String(health.status || 'UNKNOWN').toUpperCase();
  if (statusBadge) {
    statusBadge.className = statusText === 'UP' ? 'badge badge--ok' : 'badge badge--danger';
    statusBadge.textContent = statusText;
  }
  if (service) {
    service.textContent = textOrDash(health.service);
  }
  if (time) {
    time.textContent = fmtDate(health.timestamp);
  }
}

function renderPayloadStatuses(statusRows, total) {
  const node = qs('overviewPayloadStatuses');
  if (!node) return;
  if (!statusRows.length) {
    node.innerHTML = `
      <div class="overview-metric">
        <div class="overview-metric__label">No statuses</div>
        <div class="overview-metric__value">--</div>
      </div>
    `;
    return;
  }

  const cards = statusRows.map((entry) => `
    <div class="overview-metric">
      <div class="overview-metric__label">${esc(entry.status)}</div>
      <div class="overview-metric__value">${esc(entry.count)}</div>
      <div class="overview-metric__meta">${esc(pct(entry.count, total))} of all payloads</div>
    </div>
  `).join('');

  node.innerHTML = cards;
}

function renderBandDistribution(totalDevices, bands) {
  const node = qs('overviewBandRows');
  if (!node) return;
  if (!bands.length) {
    node.innerHTML = '<div class="muted">No distribution data available.</div>';
    return;
  }

  node.innerHTML = bands.map((entry) => {
    const widthPct = totalDevices ? Math.max(2, (entry.count / totalDevices) * 100) : 0;
    return `
      <div class="${barClassForBand(entry.band)}">
        <div class="overview-bar__label">${esc(entry.band)}</div>
        <div class="overview-bar__track">
          <div class="overview-bar__fill" style="width:${widthPct.toFixed(1)}%"></div>
        </div>
        <div class="overview-bar__meta">${esc(entry.count)} (${esc(pct(entry.count, totalDevices))})</div>
      </div>
    `;
  }).join('');
}

function renderTopTenants(sampleRows) {
  const body = qs('overviewTopTenantsBody');
  if (!body) return;
  if (!sampleRows.length) {
    body.innerHTML = '<tr><td colspan="3" class="muted">No payload activity in recent sample.</td></tr>';
    return;
  }

  const grouped = new Map();
  sampleRows.forEach((row) => {
    const tenant = String(row?.tenant_id || '').trim() || 'GLOBAL/UNKNOWN';
    const receivedAt = row?.received_at || null;
    const current = grouped.get(tenant) || { tenant, count: 0, latest: null };
    current.count += 1;
    if (receivedAt) {
      const currentDate = current.latest ? new Date(current.latest) : null;
      const candidateDate = new Date(receivedAt);
      if (!currentDate || candidateDate > currentDate) {
        current.latest = receivedAt;
      }
    }
    grouped.set(tenant, current);
  });

  const rows = [...grouped.values()]
    .sort((a, b) => b.count - a.count || a.tenant.localeCompare(b.tenant))
    .slice(0, 8);

  body.innerHTML = rows.map((row) => `
    <tr>
      <td>${esc(row.tenant)}</td>
      <td>${esc(row.count)}</td>
      <td>${esc(row.latest ? fmtDate(row.latest) : '--')}</td>
    </tr>
  `).join('');
}

function setFleetTelemetryStatus(message, isError = false) {
  const node = qs('overviewFleetTelemetryStatus');
  if (!node) return;
  const text = String(message || '').trim();
  node.textContent = text;
  node.hidden = text.length === 0;
  node.classList.toggle('error', Boolean(isError));
}

function setQueueHealthStatus(message, isError = false) {
  const node = qs('overviewQueueHealthStatus');
  if (!node) return;
  const text = String(message || '').trim();
  node.textContent = text;
  node.hidden = text.length === 0;
  node.classList.toggle('error', Boolean(isError));
}

function setOperationsStatus(message, isError = false) {
  const node = qs('overviewOperationsStatus');
  if (!node) return;
  const text = String(message || '').trim();
  node.textContent = text;
  node.hidden = text.length === 0;
  node.classList.toggle('error', Boolean(isError));
}

function queueBadgeClass(status) {
  const normalized = String(status || '').trim().toUpperCase();
  switch (normalized) {
    case 'HEALTHY':
      return 'badge badge--ok';
    case 'BACKLOG':
      return 'badge badge--warn';
    case 'DLQ_BACKLOG':
    case 'CONSUMER_GAP':
    case 'UNAVAILABLE':
      return 'badge badge--danger';
    default:
      return 'badge';
  }
}

function renderFleetTelemetry(summary, trendRows, agentVersions, capabilityRows) {
  const panel = qs('overviewFleetTelemetry');
  if (!panel) return;

  const latestTrend = Array.isArray(trendRows) && trendRows.length ? trendRows[trendRows.length - 1] : null;
  const topVersion = Array.isArray(agentVersions) && agentVersions.length ? agentVersions[0] : null;
  const topCapability = Array.isArray(capabilityRows) && capabilityRows.length ? capabilityRows[0] : null;
  const latestCapture = pick(topVersion, 'latestCaptureTime', 'latest_capture_time')
    || pick(topCapability, 'latestCaptureTime', 'latest_capture_time');

  qs('overviewStaleDevices').textContent = String(numberOrZero(pick(summary, 'staleDevices', 'stale_devices')));
  qs('overviewCriticalDevices').textContent = String(numberOrZero(pick(summary, 'criticalDevices', 'critical_devices')));
  qs('overviewAvgScore').textContent = formatDecimal(pick(latestTrend, 'averageTrustScore', 'average_trust_score'));
  qs('overviewTrendEvaluations').textContent = String(numberOrZero(pick(latestTrend, 'evaluationCount', 'evaluation_count')));
  qs('overviewStaleThreshold').textContent = `${numberOrZero(pick(summary, 'staleAfterHours', 'stale_after_hours'))} hours`;
  qs('overviewTrendWindow').textContent = '7 days';
  qs('overviewTopAgentVersion').textContent = textOrDash(pick(topVersion, 'agentVersion', 'agent_version'));
  qs('overviewTopAgentCompatibility').textContent = textOrDash(pick(topVersion, 'schemaCompatibilityStatus', 'schema_compatibility_status'));
  qs('overviewTopCapability').textContent = textOrDash(pick(topCapability, 'capabilityKey', 'capability_key'));
  qs('overviewTelemetryLatestCapture').textContent = fmtDate(latestCapture);

  panel.hidden = false;
  setFleetTelemetryStatus('');
}

function renderQueueHealth(summary) {
  const rowsNode = qs('overviewQueueHealthRows');
  const overallNode = qs('overviewQueueHealthOverall');
  const checkedAtNode = qs('overviewQueueHealthCheckedAt');
  if (!rowsNode || !overallNode || !checkedAtNode) return;

  const queues = Array.isArray(pick(summary, 'queues')) ? pick(summary, 'queues') : [];
  const overallStatus = textOrDash(pick(summary, 'overallStatus', 'overall_status'));
  overallNode.innerHTML = `<span class="${queueBadgeClass(overallStatus)}">${esc(overallStatus)}</span>`;
  checkedAtNode.textContent = fmtDate(pick(summary, 'checkedAt', 'checked_at'));

  if (!queues.length) {
    rowsNode.innerHTML = `
      <div class="overview-metric">
        <div class="overview-metric__label">No queue data</div>
        <div class="overview-metric__value">--</div>
      </div>
    `;
    return;
  }

  rowsNode.innerHTML = queues.map((queue) => `
    <div class="overview-metric">
      <div class="overview-metric__label">${esc(textOrDash(pick(queue, 'pipelineKey', 'pipeline_key')))}</div>
      <div class="overview-metric__value">${esc(numberOrZero(pick(queue, 'readyMessages', 'ready_messages')))} ready / ${esc(numberOrZero(pick(queue, 'deadLetterMessages', 'dead_letter_messages')))} dlq</div>
      <div class="overview-metric__meta">
        ${esc(textOrDash(pick(queue, 'status')))}
        · ${esc(numberOrZero(pick(queue, 'activeConsumers', 'active_consumers')))} active
        · ${esc(numberOrZero(pick(queue, 'configuredConsumers', 'configured_consumers')))} configured
      </div>
    </div>
  `).join('');
  setQueueHealthStatus('');
}

let failedPayloadsTable = null;

function renderOperationsSummary(summary) {
  const panel = qs('overviewOperationsPanel');
  if (!panel) return;

  qs('overviewInFlightPayloads').textContent = String(numberOrZero(pick(summary, 'inFlightPayloads', 'in_flight_payloads')));
  qs('overviewFailedLast24Hours').textContent = String(numberOrZero(pick(summary, 'failedLast24Hours', 'failed_last_24_hours')));
  qs('overviewQueueFailures7d').textContent = String(numberOrZero(pick(summary, 'queueFailuresLast7Days', 'queue_failures_last_7_days')));
  qs('overviewEvaluationFailures7d').textContent = String(numberOrZero(pick(summary, 'evaluationFailuresLast7Days', 'evaluation_failures_last_7_days')));
  qs('overviewReceivedPayloads').textContent = String(numberOrZero(pick(summary, 'receivedPayloads', 'received_payloads')));
  qs('overviewQueuedPayloads').textContent = String(numberOrZero(pick(summary, 'queuedPayloads', 'queued_payloads')));
  qs('overviewValidatedPayloads').textContent = String(numberOrZero(pick(summary, 'validatedPayloads', 'validated_payloads')));
  qs('overviewFailedPayloads').textContent = String(numberOrZero(pick(summary, 'failedPayloads', 'failed_payloads')));
  qs('overviewOldestInFlightAt').textContent = fmtDate(pick(summary, 'oldestInFlightReceivedAt', 'oldest_in_flight_received_at'));
  qs('overviewOldestInFlightAge').textContent = fmtDurationMinutes(pick(summary, 'oldestInFlightAgeMinutes', 'oldest_in_flight_age_minutes'));

  panel.hidden = false;
  setOperationsStatus('');
}

function renderPipelineTrend(rows) {
  const body = qs('overviewPipelineTrendBody');
  if (!body) return;
  if (!Array.isArray(rows) || rows.length === 0) {
    body.innerHTML = '<tr><td colspan="7" class="muted">No recent pipeline activity.</td></tr>';
    return;
  }

  body.innerHTML = rows.map((row) => `
    <tr>
      <td>${esc(fmtDateOnly(pick(row, 'bucketDate', 'bucket_date')))}</td>
      <td>${esc(numberOrZero(pick(row, 'ingestSuccessCount', 'ingest_success_count')))}</td>
      <td>${esc(numberOrZero(pick(row, 'queueSuccessCount', 'queue_success_count')))}</td>
      <td>${esc(numberOrZero(pick(row, 'queueFailureCount', 'queue_failure_count')))}</td>
      <td>${esc(numberOrZero(pick(row, 'evaluationSuccessCount', 'evaluation_success_count')))}</td>
      <td>${esc(numberOrZero(pick(row, 'evaluationFailureCount', 'evaluation_failure_count')))}</td>
      <td>${esc(numberOrZero(pick(row, 'failedPayloadCount', 'failed_payload_count')))}</td>
    </tr>
  `).join('');
}

function renderFailureCategories(rows) {
  const body = qs('overviewFailureCategoriesBody');
  if (!body) return;
  if (!Array.isArray(rows) || rows.length === 0) {
    body.innerHTML = '<tr><td colspan="4" class="muted">No recent failed payload categories.</td></tr>';
    return;
  }

  body.innerHTML = rows.map((row) => `
    <tr>
      <td>${esc(textOrDash(pick(row, 'categoryKey', 'category_key')))}</td>
      <td>${esc(numberOrZero(pick(row, 'failureCount', 'failure_count')))}</td>
      <td>${esc(fmtDate(pick(row, 'latestFailureAt', 'latest_failure_at')))}</td>
      <td>${esc(textOrDash(pick(row, 'sampleProcessError', 'sample_process_error')))}</td>
    </tr>
  `).join('');
}

function initFailedPayloadsTable() {
  if (failedPayloadsTable || !qs('overviewFailedPayloadsTable')) {
    return failedPayloadsTable;
  }

  failedPayloadsTable = window.mdmInitDataTable('#overviewFailedPayloadsTable', {
    ajax: { url: '/v1/operations/pipeline/failed-payloads/table' },
    pageLength: 10,
    defaultSortBy: 'processed_at',
    defaultSortDir: 'desc',
    extraParams: () => ({ days: 7 }),
    columns: [
      { data: 'id' },
      { data: 'tenant_id' },
      { data: 'device_external_id' },
      {
        data: 'failure_category',
        render: (value, type, row, helpers) => helpers.escapeHtml(textOrDash(value))
      },
      {
        data: 'process_error',
        render: (value, type, row, helpers) => helpers.escapeHtml(textOrDash(value))
      },
      {
        data: 'schema_compatibility_status',
        render: (value, type, row, helpers) => helpers.escapeHtml(textOrDash(value))
      },
      {
        data: 'received_at',
        render: (value, type, row, helpers) => helpers.escapeHtml(fmtDate(value))
      },
      {
        data: 'processed_at',
        render: (value, type, row, helpers) => helpers.escapeHtml(fmtDate(value))
      }
    ]
  });

  return failedPayloadsTable;
}

async function loadOverviewDetails() {
  const healthPromise = apiFetch('/v1/health').catch((error) => ({ __error: error?.message || 'Health check failed' }));
  const payloadSamplePromise = queryDataTable('/v1/ui/datatables/posture-payloads', {
    length: 200,
    sort_by: 'received_at',
    sort_dir: 'desc'
  }).catch(() => ({ recordsTotal: 0, data: [] }));
  const fleetTelemetryPromise = Promise.all([
    queryReport('/v1/reports/fleet/summary', { stale_after_hours: 72 }),
    queryReport('/v1/reports/fleet/score-trend', { days: 7 }),
    queryReport('/v1/reports/fleet/agent-versions', { limit: 1 }),
    queryReport('/v1/reports/fleet/agent-capabilities', { limit: 1 })
  ]).then(([summary, trendRows, agentVersions, capabilityRows]) => ({
    summary,
    trendRows,
    agentVersions,
    capabilityRows
  })).catch((error) => ({ __error: error?.message || 'Fleet telemetry unavailable' }));
  const queueHealthPromise = qs('overviewQueueHealthRows')
    ? apiFetch('/v1/operations/queues/summary').catch((error) => ({ __error: error?.message || 'Queue health unavailable' }))
    : Promise.resolve(null);
  const operationsPromise = qs('overviewOperationsPanel')
    ? Promise.all([
      apiFetch('/v1/operations/pipeline/summary'),
      apiFetch('/v1/operations/pipeline/trend?days=7'),
      apiFetch('/v1/operations/pipeline/failure-categories?days=7&limit=6')
    ]).then(([summary, trendRows, failureCategories]) => ({
      summary,
      trendRows,
      failureCategories
    })).catch((error) => ({ __error: error?.message || 'Pipeline operability unavailable' }))
    : Promise.resolve(null);

  const [healthResult, payloadSample, fleetTelemetryResult, queueHealthResult, operationsResult] = await Promise.all([
    healthPromise,
    payloadSamplePromise,
    fleetTelemetryPromise,
    queueHealthPromise,
    operationsPromise
  ]);

  if (healthResult && healthResult.__error) {
    updateHealthView(null, healthResult.__error);
  } else {
    updateHealthView(healthResult, null);
  }

  const sampleRows = Array.isArray(payloadSample?.data) ? payloadSample.data : [];
  const totalPayloads = numberOrZero(payloadSample?.recordsTotal ?? payloadSample?.records_total ?? 0);
  renderTopTenants(sampleRows);

  const sampleStatuses = [...new Set(sampleRows
    .map((row) => String(row?.process_status || '').trim())
    .filter((status) => status.length > 0))]
    .slice(0, 6);

  const statusesToCount = sampleStatuses.length ? sampleStatuses : ['UNKNOWN'];
  const statusRows = await Promise.all(statusesToCount.map(async (status) => ({
    status,
    count: await queryCount('/v1/ui/datatables/posture-payloads', { process_status: status }).catch(() => 0)
  })));
  renderPayloadStatuses(statusRows, totalPayloads);

  const totalDevices = await queryCount('/v1/ui/datatables/device-trust-profiles').catch(() => readFleetBase().total);
  const bandRows = await Promise.all(SCORE_BANDS.map(async (band) => ({
    band,
    count: await queryCount('/v1/ui/datatables/device-trust-profiles', { score_band: band }).catch(() => 0)
  })));
  renderBandDistribution(totalDevices, bandRows);

  if (fleetTelemetryResult?.__error) {
    setFleetTelemetryStatus(`Fleet telemetry unavailable: ${fleetTelemetryResult.__error}`, true);
  } else if (fleetTelemetryResult) {
    renderFleetTelemetry(
      fleetTelemetryResult.summary,
      fleetTelemetryResult.trendRows,
      fleetTelemetryResult.agentVersions,
      fleetTelemetryResult.capabilityRows
    );
  }

  if (queueHealthResult?.__error) {
    setQueueHealthStatus(`Queue health unavailable: ${queueHealthResult.__error}`, true);
  } else if (queueHealthResult) {
    renderQueueHealth(queueHealthResult);
  }

  if (operationsResult?.__error) {
    setOperationsStatus(`Pipeline operability unavailable: ${operationsResult.__error}`, true);
  } else if (operationsResult) {
    renderOperationsSummary(operationsResult.summary);
    renderPipelineTrend(operationsResult.trendRows);
    renderFailureCategories(operationsResult.failureCategories);
    const table = initFailedPayloadsTable();
    if (table) {
      await table.ajax.reload();
    }
  }
}

async function inspectDevice(e) {
  e.preventDefault();
  const deviceId = (qs('deviceId')?.value || '').trim();
  if (!deviceId) return;

  const resultWrap = qs('device-lookup-result');
  const errorEl = qs('device-lookup-error');
  const summaryEl = qs('device-lookup-summary');
  const rawEl = qs('device-lookup-raw');
  const profileEl = qs('device-lookup-profile');
  const snapshotEl = qs('device-lookup-snapshot');
  const decisionEl = qs('device-lookup-decision');
  const openLink = qs('device-lookup-open-link');

  if (resultWrap) resultWrap.hidden = false;
  if (errorEl) {
    errorEl.hidden = true;
    errorEl.textContent = '';
  }
  if (openLink) {
    openLink.hidden = true;
    openLink.removeAttribute('href');
  }
  if (summaryEl) {
    summaryEl.innerHTML = '<div class="muted">Loading...</div>';
  }

  try {
    const headers = tenantHeaders();
    const [profiles, snapshot, decisions, payloads, runs] = await Promise.all([
      apiFetch(`/v1/devices/trust-profiles?device_external_id=${encodeURIComponent(deviceId)}&size=1`, { headers }).catch(() => []),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/snapshots/latest`, { headers }).catch(() => null),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/decisions?size=1`, { headers }).catch(() => []),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/posture-payloads?size=1`, { headers }).catch(() => []),
      apiFetch(`/v1/devices/${encodeURIComponent(deviceId)}/evaluation-runs?size=1`, { headers }).catch(() => [])
    ]);

    const profile = Array.isArray(profiles) ? (profiles[0] || null) : profiles;
    const latestDecision = Array.isArray(decisions) ? (decisions[0] || null) : null;
    const latestPayload = Array.isArray(payloads) ? (payloads[0] || null) : null;
    const latestRun = Array.isArray(runs) ? (runs[0] || null) : null;
    const effectiveTenantId = pick(profile, 'tenant_id', 'tenantId') || (qs('tenantId')?.value || '').trim();

    if (!profile && !snapshot && !latestDecision && !latestPayload && !latestRun) {
      throw new Error('No data found for this device');
    }

    if (openLink) {
      openLink.href = buildDeviceDetailHref(deviceId, effectiveTenantId);
      openLink.hidden = false;
    }

    if (summaryEl) {
      // Clear any existing content
      summaryEl.innerHTML = '';

      const makeStat = (labelText) => {
        const stat = document.createElement('div');
        stat.className = 'stat';

        const label = document.createElement('div');
        label.className = 'stat-label';
        label.textContent = labelText;

        const value = document.createElement('div');
        value.className = 'stat-value';

        stat.appendChild(label);
        stat.appendChild(value);

        return { stat, value };
      };

      // Device
      {
        const { stat, value } = makeStat('Device');
        value.textContent = String(deviceId);
        summaryEl.appendChild(stat);
      }

      // Posture status
      {
        const { stat, value } = makeStat('Posture status');
        value.textContent = textOrDash(pick(profile, 'posture_status', 'postureStatus'));
        summaryEl.appendChild(stat);
      }

      // Trust score
      {
        const { stat, value } = makeStat('Trust score');
        value.textContent = textOrDash(pick(profile, 'current_score', 'currentScore'));
        summaryEl.appendChild(stat);
      }

      // Score band (renderBandBadge returns HTML markup)
      {
        const { stat, value } = makeStat('Score band');
        value.innerHTML = renderBandBadge(pick(profile, 'score_band', 'scoreBand'));
        summaryEl.appendChild(stat);
      }

      // Decision
      {
        const { stat, value } = makeStat('Decision');
        value.textContent = textOrDash(pick(latestDecision, 'decision_action', 'decisionAction'));
        summaryEl.appendChild(stat);
      }
    }

    renderKeyValues(profileEl, [
      { label: 'Profile ID', value: pick(profile, 'id') },
      { label: 'Tenant', value: pick(profile, 'tenant_id', 'tenantId') },
      { label: 'OS type', value: pick(profile, 'os_type', 'osType') },
      { label: 'OS name', value: pick(profile, 'os_name', 'osName') },
      { label: 'Last event', value: fmtDate(pick(profile, 'last_event_at', 'lastEventAt')) },
      { label: 'Last recalculated', value: fmtDate(pick(profile, 'last_recalculated_at', 'lastRecalculatedAt')) }
    ]);

    renderKeyValues(snapshotEl, [
      { label: 'Snapshot ID', value: pick(snapshot, 'id') },
      { label: 'Capture time', value: fmtDate(pick(snapshot, 'capture_time', 'captureTime')) },
      { label: 'Device type', value: pick(snapshot, 'device_type', 'deviceType') },
      { label: 'OS version', value: pick(snapshot, 'os_version', 'osVersion') },
      { label: 'Root detected', value: boolText(pick(snapshot, 'root_detected', 'rootDetected')) },
      { label: 'Emulator', value: boolText(pick(snapshot, 'running_on_emulator', 'runningOnEmulator')) }
    ]);

    renderKeyValues(decisionEl, [
      { label: 'Decision ID', value: pick(latestDecision, 'id') },
      { label: 'Decision action', value: pick(latestDecision, 'decision_action', 'decisionAction') },
      { label: 'Remediation required', value: boolText(pick(latestDecision, 'remediation_required', 'remediationRequired')) },
      { label: 'Latest payload ID', value: pick(latestPayload, 'id') },
      { label: 'Payload status', value: pick(latestPayload, 'process_status', 'processStatus') },
      { label: 'Latest run ID', value: pick(latestRun, 'id') }
    ]);

    if (rawEl) {
      rawEl.textContent = JSON.stringify({
        profile,
        snapshot,
        latestDecision,
        latestPayload,
        latestRun
      }, null, 2);
    }
  } catch (err) {
    if (summaryEl) summaryEl.innerHTML = '';
    if (profileEl) profileEl.innerHTML = '';
    if (snapshotEl) snapshotEl.innerHTML = '';
    if (decisionEl) decisionEl.innerHTML = '';
    if (rawEl) rawEl.textContent = 'No data.';
    if (errorEl) {
      errorEl.hidden = false;
      errorEl.textContent = `Error loading device data: ${err.message}`;
    }
    if (openLink) {
      openLink.hidden = true;
      openLink.removeAttribute('href');
    }
  }
}

function initRecentPayloadTable() {
  window.mdmInitDataTable('#recent-payloads', {
    ajax: { url: '/v1/ui/datatables/posture-payloads', dataSrc: 'data' },
    pageLength: 10,
    defaultSortBy: 'received_at',
    defaultSortDir: 'desc',
    columns: [
      { data: 'id' },
      { data: 'tenant_id' },
      { data: 'device_external_id' },
      {
        data: 'process_status',
        render: (value, type, row, helpers) => `<span class="${badgeClassForStatus(value)}">${helpers.escapeHtml(value || '')}</span>`
      },
      { data: 'received_at' },
      {
        data: 'received_at',
        orderable: false,
        render: (value, type, row, helpers) => helpers.escapeHtml(fmtAge(value))
      }
    ]
  });
}

document.addEventListener('DOMContentLoaded', async () => {
  renderDerivedFleetStats();
  qs('device-lookup-form')?.addEventListener('submit', inspectDevice);
  qs('overviewRefreshBtn')?.addEventListener('click', () => {
    loadOverviewDetails().catch((error) => window.mdmToast?.(`Overview refresh failed: ${error.message}`));
  });
  initRecentPayloadTable();
  initFailedPayloadsTable();

  try {
    await loadOverviewDetails();
  } catch (error) {
    window.mdmToast?.(`Failed to load overview details: ${error.message}`);
  }
});
