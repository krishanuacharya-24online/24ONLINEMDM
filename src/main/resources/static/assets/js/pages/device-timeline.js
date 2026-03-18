/**
 * Device History Timeline Visualization
 * Displays trust score changes, evaluations, and decisions in an interactive timeline.
 */

const EVENT_COLORS = {
  SCORE: { bg: '#e3f2fd', border: '#2196f3', text: '#1565c0' },
  SECURITY: { bg: '#ffebee', border: '#f44336', text: '#c62828' },
  APPLICATION: { bg: '#fff3e0', border: '#ff9800', text: '#e65100' },
  LIFECYCLE: { bg: '#f3e5f5', border: '#9c27b0', text: '#6a1b9a' },
  DECISION: { bg: '#e8f5e9', border: '#4caf50', text: '#2e7d32' },
  REMEDIATION: { bg: '#fff8e1', border: '#ffc107', text: '#ff8f00' },
  SYSTEM: { bg: '#eceff1', border: '#607d8b', text: '#455a64' }
};

const EVENT_ICONS = {
  SCORE_CHANGE: 'SC',
  EVALUATION: 'EV',
  DECISION: 'DS',
  REMEDIATION: 'RM',
  LIFECYCLE: 'LC',
  ENROLLMENT: 'EN',
  POSTURE_SUBMISSION: 'PS'
};

const SEVERITY_INDICATORS = {
  INFO: { icon: '[i]', class: 'severity-info' },
  WARNING: { icon: '[!]', class: 'severity-warning' },
  CRITICAL: { icon: '[x]', class: 'severity-critical' }
};

function esc(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function toSnakeCase(camelKey) {
  return camelKey.replace(/[A-Z]/g, (ch) => `_${ch.toLowerCase()}`);
}

function getField(obj, camelKey) {
  if (!obj) return undefined;
  if (obj[camelKey] !== undefined && obj[camelKey] !== null) {
    return obj[camelKey];
  }
  const snakeKey = toSnakeCase(camelKey);
  if (obj[snakeKey] !== undefined && obj[snakeKey] !== null) {
    return obj[snakeKey];
  }
  return undefined;
}

function toNumberOrNull(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const num = Number(value);
  return Number.isFinite(num) ? num : null;
}

function toBoolean(value) {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value !== 0;
  if (typeof value === 'string') return value.trim().toLowerCase() === 'true';
  return false;
}

function normalizeTimelineEvent(rawEvent) {
  if (!rawEvent || typeof rawEvent !== 'object') {
    return {
      id: null,
      timestamp: null,
      type: 'SYSTEM',
      scoreBefore: null,
      scoreAfter: null,
      scoreDelta: null,
      decisionAction: null,
      title: 'Timeline Event',
      description: '',
      category: 'SYSTEM',
      severity: 'INFO',
      ruleName: null,
      remediationRequired: false,
      metadata: null
    };
  }

  const type = String(getField(rawEvent, 'type') || 'SYSTEM').toUpperCase();
  const category = String(getField(rawEvent, 'category') || 'SYSTEM').toUpperCase();
  const severity = String(getField(rawEvent, 'severity') || 'INFO').toUpperCase();

  return {
    id: getField(rawEvent, 'id'),
    timestamp: getField(rawEvent, 'timestamp'),
    type,
    scoreBefore: toNumberOrNull(getField(rawEvent, 'scoreBefore')),
    scoreAfter: toNumberOrNull(getField(rawEvent, 'scoreAfter')),
    scoreDelta: toNumberOrNull(getField(rawEvent, 'scoreDelta')),
    decisionAction: getField(rawEvent, 'decisionAction'),
    title: getField(rawEvent, 'title') || 'Timeline Event',
    description: getField(rawEvent, 'description') || '',
    category,
    severity,
    ruleName: getField(rawEvent, 'ruleName'),
    remediationRequired: toBoolean(getField(rawEvent, 'remediationRequired')),
    metadata: getField(rawEvent, 'metadata')
  };
}

function normalizeTimelineResponse(rawResponse) {
  const raw = rawResponse && typeof rawResponse === 'object' ? rawResponse : {};
  const rawEvents = getField(raw, 'events');
  const normalizedEvents = Array.isArray(rawEvents)
    ? rawEvents.map(normalizeTimelineEvent)
    : [];

  const totalEvents = toNumberOrNull(getField(raw, 'totalEvents'));

  return {
    deviceExternalId: getField(raw, 'deviceExternalId'),
    deviceType: getField(raw, 'deviceType'),
    osType: getField(raw, 'osType'),
    currentScore: toNumberOrNull(getField(raw, 'currentScore')),
    scoreBand: getField(raw, 'scoreBand'),
    currentDecision: getField(raw, 'currentDecision'),
    events: normalizedEvents,
    totalEvents: totalEvents !== null ? totalEvents : normalizedEvents.length,
    startTime: getField(raw, 'startTime'),
    endTime: getField(raw, 'endTime')
  };
}

function formatTimestamp(timestamp) {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
}

function formatRelativeTime(timestamp) {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) return '';

  const now = new Date();
  const diffMs = now - date;
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffMins < 1) return 'Just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return formatTimestamp(timestamp);
}

function getScoreBandColor(scoreBand) {
  const colors = {
    TRUSTED: '#388e3c',
    LOW_RISK: '#7cb342',
    MEDIUM_RISK: '#fbc02d',
    HIGH_RISK: '#f57c00',
    CRITICAL: '#d32f2f',
    UNKNOWN: '#9e9e9e'
  };
  return colors[scoreBand] || colors.UNKNOWN;
}

function getDecisionColor(decision) {
  const colors = {
    ALLOW: '#4caf50',
    NOTIFY: '#2196f3',
    QUARANTINE: '#ff9800',
    BLOCK: '#f44336'
  };
  return colors[decision] || '#9e9e9e';
}

function createTimelineEventCard(rawEvent) {
  const event = normalizeTimelineEvent(rawEvent);
  const category = EVENT_COLORS[event.category] || EVENT_COLORS.SYSTEM;
  const icon = EVENT_ICONS[event.type] || 'EV';
  const severity = SEVERITY_INDICATORS[event.severity] || SEVERITY_INDICATORS.INFO;

  const scoreChange = event.scoreDelta !== null;
  const scoreChangeClass = scoreChange ? (event.scoreDelta > 0 ? 'score-positive' : 'score-negative') : '';
  const scoreChangeText = scoreChange ? `${event.scoreDelta > 0 ? '+' : ''}${event.scoreDelta}` : '';

  return `
    <div class="timeline-event" data-event-id="${esc(event.id)}">
      <div class="timeline-event-marker" style="background: ${category.border}">
        ${esc(icon)}
      </div>
      <div class="timeline-event-content" style="background: ${category.bg}; border-left-color: ${category.border}">
        <div class="timeline-event-header">
          <div class="timeline-event-title">
            <span class="timeline-event-icon">${esc(icon)}</span>
            <span>${esc(event.title)}</span>
          </div>
          <div class="timeline-event-time">
            <span title="${esc(formatTimestamp(event.timestamp))}">${esc(formatRelativeTime(event.timestamp))}</span>
          </div>
        </div>

        <div class="timeline-event-body">
          <p class="timeline-event-description">${esc(event.description)}</p>

          ${event.ruleName ? `<div class="timeline-event-rule">Rule: ${esc(event.ruleName)}</div>` : ''}

          ${scoreChange ? `
            <div class="timeline-event-score ${scoreChangeClass}">
              Score: ${esc(event.scoreBefore !== null ? event.scoreBefore : '?')}
              -> ${esc(event.scoreAfter !== null ? event.scoreAfter : '?')}
              <span class="score-delta">${esc(scoreChangeText)}</span>
            </div>
          ` : ''}

          ${event.decisionAction ? `
            <div class="timeline-event-decision">
              <span class="decision-badge" style="background: ${getDecisionColor(event.decisionAction)}">
                ${esc(event.decisionAction)}
              </span>
            </div>
          ` : ''}

          ${event.remediationRequired ? `
            <div class="timeline-event-remediation">
              Remediation required
            </div>
          ` : ''}
        </div>

        <div class="timeline-event-footer">
          <span class="timeline-event-severity ${severity.class}">
            ${esc(severity.icon)} ${esc(event.severity)}
          </span>
          <span class="timeline-event-type">${esc(event.type)}</span>
        </div>
      </div>
    </div>
  `;
}

function createTimelineSummary(rawResponse) {
  const response = normalizeTimelineResponse(rawResponse);
  const currentBandColor = getScoreBandColor(response.scoreBand);
  const decisionColor = response.currentDecision ? getDecisionColor(response.currentDecision) : '#9e9e9e';

  return `
    <div class="timeline-summary">
      <div class="timeline-summary-header">
        <h3>Device Timeline</h3>
        <div class="timeline-summary-device">
          <span class="device-chip">Device ${esc(response.deviceExternalId || 'Unknown')}</span>
          <span class="os-chip">OS ${esc(response.osType || 'Unknown')}</span>
        </div>
      </div>

      <div class="timeline-summary-stats">
        <div class="stat-card">
          <div class="stat-label">Current Score</div>
          <div class="stat-value" style="color: ${currentBandColor}">
            ${response.currentScore !== null ? esc(response.currentScore) : 'N/A'}
          </div>
          <div class="stat-sub">${esc(response.scoreBand || 'Unknown')}</div>
        </div>

        ${response.currentDecision ? `
          <div class="stat-card">
            <div class="stat-label">Current Decision</div>
            <div class="stat-value">
              <span class="decision-badge" style="background: ${decisionColor}; font-size: 0.875rem;">
                ${esc(response.currentDecision)}
              </span>
            </div>
            <div class="stat-sub">Latest decision</div>
          </div>
        ` : ''}

        <div class="stat-card">
          <div class="stat-label">Total Events</div>
          <div class="stat-value">${esc(response.totalEvents)}</div>
          <div class="stat-sub">
            ${response.startTime && response.endTime
              ? `${esc(formatRelativeTime(response.startTime))} - now`
              : 'No events'}
          </div>
        </div>
      </div>
    </div>
  `;
}

export function renderTimeline(container, response) {
  if (!container) return;

  const normalized = normalizeTimelineResponse(response);
  let html = createTimelineSummary(normalized);
  html += '<div class="timeline-events">';

  if (!normalized.events.length) {
    html += `
      <div class="timeline-empty">
        <div class="empty-icon">--</div>
        <p>No timeline events found</p>
        <p class="muted">Events will appear here as the device posture is evaluated.</p>
      </div>
    `;
  } else {
    for (const event of normalized.events) {
      html += createTimelineEventCard(event);
    }
  }

  html += '</div>';
  container.innerHTML = html;
}

export async function loadTimeline(profileId, limit = 50) {
  try {
    const response = await fetch(`/v1/devices/${profileId}/timeline?limit=${limit}`, {
      method: 'GET',
      headers: {
        Accept: 'application/json'
      },
      credentials: 'same-origin'
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Failed to load timeline:', error);
    throw error;
  }
}

export function initTimelineViewer(containerId) {
  const container = document.getElementById(containerId);
  if (!container) return null;

  return {
    container,

    async load(profileId, limit = 50) {
      try {
        container.innerHTML = '<div class="timeline-loading">Loading timeline...</div>';
        const data = await loadTimeline(profileId, limit);
        renderTimeline(container, data);
        return data;
      } catch (error) {
        container.innerHTML = `
          <div class="timeline-error">
            <div class="error-icon">X</div>
            <p>Failed to load timeline</p>
            <p class="muted">${esc(error.message)}</p>
          </div>
        `;
        throw error;
      }
    },

    refresh() {
      const profileId = container.dataset.profileId;
      if (profileId) {
        return this.load(profileId);
      }
      return Promise.resolve();
    },

    clear() {
      container.innerHTML = '';
    }
  };
}

export function startTimelineAutoRefresh(timelineViewer, profileId, intervalMs = 60000) {
  return setInterval(() => {
    timelineViewer.load(profileId);
  }, intervalMs);
}
