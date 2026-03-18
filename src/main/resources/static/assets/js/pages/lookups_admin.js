import { apiFetch } from '../api.js';

function qs(id) {
  return document.getElementById(id);
}

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

let currentType = null;
let lookupDt = null;

function setLookupContext() {
  const badge = qs('lookupTypeBadge');
  const hint = qs('lookupTypeHint');
  if (badge) {
    badge.textContent = currentType ? `Type: ${currentType}` : 'No type selected';
  }
  if (hint) {
    hint.textContent = currentType
      ? `Managing values for "${currentType}".`
      : 'Select a type to load values and manage entries.';
  }
}

function resetLookupForm(shouldFocus = false) {
  const code = qs('code');
  const description = qs('description');
  const title = qs('lookupEditorTitle');
  if (code) code.value = '';
  if (description) description.value = '';
  if (title) title.textContent = 'Create / update value';
  if (shouldFocus && code && !code.disabled) {
    code.focus();
  }
}

function setLookupFormEnabled(enabled) {
  const code = qs('code');
  const description = qs('description');
  const submitBtn = qs('lookupForm')?.querySelector('button[type="submit"]');
  const resetBtn = qs('resetLookupForm');
  if (code) code.disabled = !enabled;
  if (description) description.disabled = !enabled;
  if (submitBtn) submitBtn.disabled = !enabled;
  if (resetBtn) resetBtn.disabled = !enabled;
  if (!enabled) {
    resetLookupForm(false);
  }
}

async function loadTypes() {
  const sel = qs('lookupType');
  const previous = currentType || sel.value || '';

  sel.disabled = true;
  sel.innerHTML = '<option value="">Loading...</option>';

  const types = await apiFetch('/v1/lookups');
  const cleaned = [...new Set((types || [])
    .map((t) => t && (t.lookupType || t.lookup_type))
    .map((v) => (v || '').trim())
    .filter((v) => v.length > 0))]
    .sort((a, b) => a.localeCompare(b));

  if (!cleaned.length) {
    sel.innerHTML = '<option value="">No lookup types found</option>';
    currentType = null;
    sel.disabled = false;
    setLookupContext();
    setLookupFormEnabled(false);
    refreshLookupTable();
    return;
  }

  sel.innerHTML = '<option value="">Select...</option>';
  for (const lt of cleaned) {
    const opt = document.createElement('option');
    opt.value = lt;
    opt.textContent = lt;
    sel.appendChild(opt);
  }

  const next = cleaned.includes(previous) ? previous : cleaned[0];
  sel.value = next;
  currentType = next || null;
  sel.disabled = false;
  setLookupContext();
  setLookupFormEnabled(Boolean(currentType));
  refreshLookupTable();
}

function refreshLookupTable() {
  if (!lookupDt) {
    lookupDt = window.mdmInitDataTable('#lookupValues', {
      ajax: { url: '/v1/ui/datatables/lookups', dataSrc: 'data' },
      defaultSortBy: 'code',
      defaultSortDir: 'asc',
      extraParams: () => ({
        lookup_type: currentType || '__none__'
      }),
      columns: [
        { data: 'code' },
        { data: 'description' },
        {
          data: null,
          orderable: false,
          render: (data, type, row) =>
            `<button class="secondary" data-act="edit">Edit</button>
             <button class="danger" data-act="del">Delete</button>`
        }
      ]
    });

    qs('lookupValues')?.addEventListener('click', async (e) => {
      const btn = e.target.closest('button[data-act]');
      if (!btn) return;
      const row = btn.closest('tr') ? lookupDt.row(btn.closest('tr')).data() : null;
      if (!row) return;

      const act = btn.getAttribute('data-act');
      if (act === 'edit') {
        qs('code').value = row.code || '';
        qs('description').value = row.description || '';
        const title = qs('lookupEditorTitle');
        if (title) title.textContent = 'Edit value';
        qs('code').focus();
      } else if (act === 'del') {
        if (!currentType) return;
        const confirmed = await confirmDanger(`Delete ${currentType}:${row.code}?`);
        if (!confirmed) return;
        await apiFetch(`/v1/admin/lookups/${encodeURIComponent(currentType)}/${encodeURIComponent(row.code)}`, { method: 'DELETE' });
        window.mdmToast?.('Deleted');
        lookupDt.ajax.reload();
        resetLookupForm(false);
      }
    });
  } else {
    lookupDt.state.start = 0;
    lookupDt.ajax.reload();
  }
}

async function saveValue(e) {
  e.preventDefault();
  if (!currentType) {
    window.mdmToast?.('Pick a lookup type first');
    return;
  }

  const code = (qs('code').value || '').trim();
  const description = (qs('description').value || '').trim();
  if (!code || !description) return;

  await apiFetch(`/v1/admin/lookups/${encodeURIComponent(currentType)}`, {
    method: 'POST',
    body: JSON.stringify({ code, description })
  });

  window.mdmToast?.('Saved');
  resetLookupForm(true);
  refreshLookupTable();
}

document.addEventListener('DOMContentLoaded', async () => {
  qs('refreshTypes')?.addEventListener('click', () => loadTypes().catch((e) => window.mdmToast?.(e.message)));
  qs('resetLookupForm')?.addEventListener('click', () => resetLookupForm(true));
  qs('lookupForm')?.addEventListener('submit', (e) => saveValue(e).catch((err) => window.mdmToast?.(err.message)));
  qs('lookupType')?.addEventListener('change', async (e) => {
    currentType = e.target.value || null;
    setLookupContext();
    setLookupFormEnabled(Boolean(currentType));
    resetLookupForm(false);
    refreshLookupTable();
  });

  setLookupContext();
  setLookupFormEnabled(false);

  try {
    await loadTypes();
  } catch (e) {
    const sel = qs('lookupType');
    if (sel) {
      sel.disabled = false;
      sel.innerHTML = '<option value="">Failed to load types</option>';
    }
    setLookupContext();
    setLookupFormEnabled(false);
    window.mdmToast?.(`Failed to load lookup types: ${e.message}`);
  }
});

