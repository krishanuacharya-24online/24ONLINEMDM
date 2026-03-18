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

function setStatus(message, isError = false) {
  const status = byId('reportsStatus');
  if (!status) return;
  const text = String(message || '').trim();
  status.textContent = text;
  status.hidden = text.length === 0;
  status.classList.toggle('error', Boolean(isError));
}

function configureFrame(config) {
  const frame = byId('reportsFrame');
  const mount = byId('reportsEmbedMount');
  const actions = byId('reportsActions');
  const openLink = byId('reportsOpenLink');
  if (!frame || !mount) return;

  const iframeUrl = String(cfg(config, 'iframeUrl', 'iframe_url') || '').trim();
  if (!iframeUrl) {
    mount.hidden = true;
    mount.replaceChildren();
    frame.hidden = true;
    if (actions) actions.hidden = true;
    setStatus('Superset dashboard URL is not configured.', true);
    return;
  }

  const sandbox = String(cfg(config, 'iframeSandbox', 'iframe_sandbox') || '').trim();
  if (sandbox) {
    frame.setAttribute('sandbox', sandbox);
  } else {
    frame.removeAttribute('sandbox');
  }

  mount.hidden = true;
  mount.replaceChildren();
  frame.src = iframeUrl;
  frame.hidden = false;
  if (actions) actions.hidden = true;
  if (openLink) openLink.href = iframeUrl;
  setStatus('');
}

async function configureGuestEmbed(config) {
  const sdk = window?.supersetEmbeddedSdk;
  if (!sdk || typeof sdk.embedDashboard !== 'function') {
    setStatus('Superset embedded SDK is not loaded.', true);
    return;
  }

  const frame = byId('reportsFrame');
  const mount = byId('reportsEmbedMount');
  const actions = byId('reportsActions');
  const openLink = byId('reportsOpenLink');
  if (!frame || !mount) return;

  const supersetDomain = String(cfg(config, 'supersetDomain', 'superset_domain') || '').trim();
  let embeddedDashboardId = String(cfg(config, 'embeddedDashboardId', 'embedded_dashboard_id') || '').trim();
  const openUrl = String(cfg(config, 'iframeUrl', 'iframe_url') || '').trim();
  let initialGuestToken = '';

  const requestGuestTokenPayload = async () => {
    const response = await apiFetch('/v1/reports/superset/guest-token', { method: 'POST' });
    const token = String(cfg(response, 'token', 'token') || '').trim();
    const resourceId = String(cfg(response, 'resourceId', 'resource_id') || '').trim();
    if (!token) {
      throw new Error('Superset guest token response is missing token.');
    }
    return { token, resourceId };
  };

  if (!embeddedDashboardId || /^\d+$/.test(embeddedDashboardId)) {
    const payload = await requestGuestTokenPayload();
    initialGuestToken = payload.token;
    if (payload.resourceId) {
      embeddedDashboardId = payload.resourceId;
    }
  }

  if (!supersetDomain || !embeddedDashboardId || /^\d+$/.test(embeddedDashboardId)) {
    setStatus('Guest token mode needs a valid embedded dashboard ID (UUID).', true);
    return;
  }

  const fallbackOpenUrl = `${supersetDomain}/embedded/${embeddedDashboardId}`;
  if (openLink) openLink.href = openUrl || fallbackOpenUrl;
  if (actions) actions.hidden = true;
  frame.hidden = true;
  frame.src = '';
  mount.hidden = false;
  mount.replaceChildren();
  setStatus('');

  await sdk.embedDashboard({
    id: embeddedDashboardId,
    supersetDomain,
    mountPoint: mount,
    fetchGuestToken: async () => {
      if (initialGuestToken) {
        const token = initialGuestToken;
        initialGuestToken = '';
        return token;
      }
      const payload = await requestGuestTokenPayload();
      return payload.token;
    },
    iframeSandboxExtras: ['allow-popups-to-escape-sandbox'],
    referrerPolicy: 'strict-origin-when-cross-origin'
  });

  setStatus('');
}

async function loadSupersetConfig() {
  const config = await apiFetch('/v1/reports/superset/config');
  if (!config?.enabled) {
    const message = config?.message || 'Superset reporting is disabled.';
    setStatus(message, true);
    return;
  }

  const guestTokenEnabled = Boolean(cfg(config, 'guestTokenEnabled', 'guest_token_enabled'));
  if (guestTokenEnabled) {
    await configureGuestEmbed(config);
    return;
  }
  configureFrame(config);
}

document.addEventListener('DOMContentLoaded', async () => {
  try {
    await loadSupersetConfig();
  } catch (error) {
    setStatus(`Failed to load reports: ${error.message}`, true);
  }
});
