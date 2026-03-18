import { apiFetch } from './api.js';

const LAST_SYNC_SELECTOR = '[data-sync-last="true"]';

function toCount(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function summarize(report) {
  const lifecycle = toCount(report?.lifecycle_upserts);
  const catalog = toCount(report?.app_catalog_upserts);
  const iosEnriched = toCount(report?.ios_enriched_rows);
  return `Sync complete. Lifecycle: ${lifecycle}, Catalog: ${catalog}, iOS enriched: ${iosEnriched}`;
}

function errorMessage(error) {
  const text = String(error?.message || '').trim();
  return text || 'Unable to run sync now.';
}

function formatLastSync(isoValue) {
  if (!isoValue) {
    return 'Last sync: Never';
  }
  const date = new Date(isoValue);
  if (Number.isNaN(date.getTime())) {
    return 'Last sync: --';
  }
  const formatter = new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  });
  return `Last sync: ${formatter.format(date)}`;
}

function renderLastSync(isoValue) {
  document.querySelectorAll(LAST_SYNC_SELECTOR).forEach((element) => {
    element.textContent = formatLastSync(isoValue);
  });
}

async function fetchStatus() {
  return apiFetch('/v1/admin/reference-sync/status');
}

async function refreshLastSyncLabel() {
  try {
    const status = await fetchStatus();
    const lastSyncAt = status?.last_sync_at || status?.finished_at || null;
    renderLastSync(lastSyncAt);
  } catch {
    renderLastSync(null);
  }
}

function closeDropdown(button) {
  const dropdown = button.closest('details.nav-dropdown');
  if (dropdown) {
    dropdown.open = false;
  }
}

async function runNow(trigger) {
  const safeTrigger = String(trigger || 'manual-ui').trim() || 'manual-ui';
  return apiFetch(`/v1/admin/reference-sync/run?trigger=${encodeURIComponent(safeTrigger)}`, {
    method: 'POST'
  });
}

function bindSyncNowButton(button) {
  if (!(button instanceof HTMLButtonElement)) return;
  if (button.dataset.syncNowBound === 'true') return;
  button.dataset.syncNowBound = 'true';

  button.addEventListener('click', async (event) => {
    event.preventDefault();
    if (button.disabled) return;

    const trigger = button.dataset.syncTrigger || 'manual-ui';
    button.disabled = true;
    closeDropdown(button);

    try {
      const report = await runNow(trigger);
      renderLastSync(report?.finished_at || null);
      const errors = Array.isArray(report?.errors) ? report.errors.filter(Boolean) : [];
      if (errors.length > 0) {
        window.mdmToast?.(`${summarize(report)} (with ${errors.length} warning(s))`);
      } else {
        window.mdmToast?.(summarize(report));
      }
      document.dispatchEvent(new CustomEvent('reference-sync:completed', {
        detail: report || null
      }));
    } catch (error) {
      window.mdmToast?.(`Sync failed: ${errorMessage(error)}`);
      document.dispatchEvent(new CustomEvent('reference-sync:failed', {
        detail: error || null
      }));
    } finally {
      button.disabled = false;
    }
  });
}

function bindAll() {
  document.querySelectorAll('button[data-sync-now="true"]').forEach((button) => {
    bindSyncNowButton(button);
  });
}

document.addEventListener('DOMContentLoaded', () => {
  const hasSyncControls = document.querySelector('button[data-sync-now="true"]');
  const hasSyncLabel = document.querySelector(LAST_SYNC_SELECTOR);
  if (!hasSyncControls && !hasSyncLabel) {
    return;
  }
  bindAll();
  refreshLastSyncLabel();
});
