import { apiFetch } from './api.js';

function el(id) {
  return document.getElementById(id);
}

function toDateInputValue(value) {
  if (value == null) return '';
  const raw = String(value).trim();
  if (!raw) return '';
  const isoDatePrefix = raw.match(/^(\d{4}-\d{2}-\d{2})/);
  if (isoDatePrefix) {
    return isoDatePrefix[1];
  }
  const parsed = new Date(raw);
  if (Number.isNaN(parsed.getTime())) {
    return '';
  }
  return parsed.toISOString().slice(0, 10);
}

function toDateStartOfDayIso(value) {
  const raw = String(value || '').trim();
  if (!raw) return null;
  return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? `${raw}T00:00:00Z` : raw;
}

function readField(input) {
  if (!input) return null;
  const tag = (input.tagName || '').toLowerCase();
  const type = (input.getAttribute('type') || '').toLowerCase();
  if (type === 'checkbox') return Boolean(input.checked);
  const v = input.value;
  if (v === '' || v == null) return null;
  if (type === 'date') return toDateStartOfDayIso(v);
  if (type === 'number') return Number(v);
  if (tag === 'select') return v === '' ? null : v;
  return v;
}

function writeField(input, value) {
  if (!input) return;
  const type = (input.getAttribute('type') || '').toLowerCase();
  if (type === 'checkbox') {
    input.checked = Boolean(value);
    return;
  }
  if (type === 'date') {
    input.value = toDateInputValue(value);
    return;
  }
  input.value = value == null ? '' : String(value);
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

export function mountDtCrud({
  tableSelector,
  ajaxUrl,
  columns,
  tableOptions = {},
  idField = 'id',
  createUrl,
  updateUrl,
  deleteUrl,
  requestHeaders = () => ({}),
  formId,
  resetId,
  fieldIds,
  defaults = () => ({}),
  beforeSave = (payload) => payload,
  canEditRow = () => true,
  canDeleteRow = () => true,
  onAction = async () => false
}) {
  function resolveRequestHeaders() {
    const dynamic = typeof requestHeaders === 'function'
      ? requestHeaders()
      : requestHeaders;
    if (!dynamic || typeof dynamic !== 'object') {
      return {};
    }
    return dynamic;
  }

  const dt = window.mdmInitDataTable(tableSelector, {
    ...tableOptions,
    requestHeaders: resolveRequestHeaders,
    ajax: { url: ajaxUrl, dataSrc: 'data' },
    columns
  });

  const form = el(formId);
  const resetBtn = el(resetId);
  const editId = el('edit_id');

  function payloadFromForm() {
    const payload = {};
    for (const id of fieldIds) {
      payload[id] = readField(el(id));
    }
    return beforeSave(payload);
  }

  function resetForm(row) {
    writeField(editId, row?.[idField] ?? null);
    const base = { ...defaults(), ...(row || {}) };
    for (const id of fieldIds) {
      writeField(el(id), base[id]);
    }
  }

  document.querySelector(tableSelector).addEventListener('click', async (e) => {
    const btn = e.target.closest('button[data-act]');
    if (!btn) return;
    const act = btn.getAttribute('data-act');

    const tr = btn.closest('tr');
    const row = tr ? dt.row(tr).data() : null;
    const id = btn.getAttribute('data-id') || row?.[idField];
    if (!id) return;

    if (act === 'edit') {
      if (!canEditRow(row)) {
        window.mdmToast?.('This record is read-only in your scope. Clone it to your tenant to make changes.');
        return;
      }
      resetForm(row);
      window.mdmToast?.(`Editing #${id}`);
    } else if (act === 'del') {
      if (!canDeleteRow(row)) {
        window.mdmToast?.('This record is read-only in your scope. Clone it to your tenant to delete it.');
        return;
      }
      const confirmed = await confirmDanger(`Delete #${id}?`);
      if (!confirmed) return;
      await apiFetch(deleteUrl(id), {
        method: 'DELETE',
        headers: resolveRequestHeaders()
      });
      window.mdmToast?.('Deleted');
      dt.ajax.reload(null, false);
      resetForm(null);
    } else {
      const handled = await onAction({
        act,
        id,
        row,
        requestHeaders: resolveRequestHeaders,
        reload: () => dt.ajax.reload(null, false),
        reset: () => resetForm(null),
        fill: (value) => resetForm(value)
      });
      if (handled) {
        return;
      }
    }
  });

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = readField(editId);
    const payload = payloadFromForm();
    if (!id) {
      await apiFetch(createUrl, {
        method: 'POST',
        body: JSON.stringify(payload),
        headers: resolveRequestHeaders()
      });
      window.mdmToast?.('Created');
    } else {
      await apiFetch(updateUrl(id), {
        method: 'PUT',
        body: JSON.stringify(payload),
        headers: resolveRequestHeaders()
      });
      window.mdmToast?.('Saved');
    }
    dt.ajax.reload(null, false);
    resetForm(null);
  });

  resetBtn.addEventListener('click', () => resetForm(null));
  resetForm(null);

  return {
    table: dt,
    reload: () => dt.ajax.reload(null, false),
    reset: () => resetForm(null)
  };
}
