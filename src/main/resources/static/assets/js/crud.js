import { apiFetch } from './api.js';

function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v;
    else if (k === 'text') node.textContent = v;
    else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v);
  }
  for (const c of children) node.appendChild(c);
  return node;
}

function displayText(value) {
  return String(value ?? '')
    .replaceAll('\uFFFD', '')
    .replaceAll('ï¿½', '')
    .replaceAll('â€¦', '...')
    .replaceAll('â€“', '-')
    .replaceAll('â€”', '-')
    .replaceAll('â€œ', '"')
    .replaceAll('â€�', '"')
    .trim();
}

function inputFor(field) {
  const common = { name: field.name, id: field.name };
  if (field.type === 'boolean') {
    return el('input', { ...common, type: 'checkbox' }, []);
  }
  if (field.type === 'select') {
    const s = el('select', common, (field.options || []).map((o) =>
      el('option', { value: o.value ?? o, text: o.label ?? o })
    ));
    return s;
  }
  if (field.type === 'textarea') {
    return el('textarea', { ...common, placeholder: field.placeholder || '' }, []);
  }
  const t = field.type === 'number' ? 'number' : 'text';
  return el('input', { ...common, type: t, placeholder: field.placeholder || '' }, []);
}

function readValue(field, form) {
  const node = form.querySelector(`[name="${field.name}"]`);
  if (!node) return undefined;

  if (field.type === 'number') {
    return node.value === '' ? null : Number(node.value);
  }
  if (field.type === 'boolean') {
    return node.checked;
  }
  return node.value === '' ? null : node.value;
}

function writeValue(field, form, value) {
  const node = form.querySelector(`[name="${field.name}"]`);
  if (!node) return;
  if (field.type === 'boolean') {
    node.checked = Boolean(value);
    return;
  }
  node.value = value ?? '';
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

export function mountCrud(root, config) {
  const state = {
    items: [],
    editingId: null
  };

  const header = el('div', { class: 'page-head' }, [
    el('div', {}, [
      el('h2', { text: config.title }),
      el('div', { class: 'muted', text: config.subtitle || '' })
    ]),
    el('div', { class: 'form-row' }, [
      el('button', { text: 'New', onclick: () => startCreate() }),
      el('button', { class: 'secondary', text: 'Refresh', onclick: () => load() })
    ])
  ]);

  const table = el('table', {}, []);
  const form = el('form', { class: 'card' }, []);
  const split = el('div', { class: 'split' }, [
    el('div', { class: 'card' }, [table]),
    form
  ]);

  root.replaceChildren(header, split);

  function renderTable() {
    const cols = config.columns;
    const thead = el('thead', {}, [
      el('tr', {}, [
        ...cols.map((c) => el('th', { text: c.label })),
        el('th', { text: 'Actions' })
      ])
    ]);

    const tbody = el('tbody', {}, []);
    if (!state.items.length) {
      tbody.appendChild(el('tr', {}, [el('td', { class: 'muted', colspan: String(cols.length + 1), text: 'No rows.' })]));
    } else {
      for (const row of state.items) {
        const tr = el('tr', {}, [
          ...cols.map((c) => el('td', { text: displayText(row[c.name]) })),
          el('td', {}, [
            el('button', { class: 'secondary', type: 'button', text: 'Edit', onclick: () => startEdit(row[config.idField]) }),
            el('span', { text: ' ' }),
            el('button', { class: 'danger', type: 'button', text: 'Delete', onclick: () => del(row[config.idField]) })
          ])
        ]);
        tbody.appendChild(tr);
      }
    }

    table.replaceChildren(thead, tbody);
  }

  function renderForm() {
    form.innerHTML = '';
    form.appendChild(el('h2', { text: state.editingId ? `Edit #${state.editingId}` : 'Create' }));

    const fieldsWrap = el('div', { class: 'kvs' }, []);
    for (const f of config.fields) {
      if (f.hidden) continue;
      const control = inputFor(f);
      if (f.required) control.required = true;
      if (f.readonly) control.disabled = true;
      const label = el('label', { for: f.name, text: f.label });
      fieldsWrap.appendChild(el('div', { class: 'k' }, [label]));
      fieldsWrap.appendChild(el('div', { class: 'v' }, [control]));
    }

    const actions = el('div', { class: 'form-row' }, [
      el('button', { type: 'submit', text: state.editingId ? 'Save' : 'Create' }),
      el('button', { class: 'secondary', type: 'button', text: 'Reset', onclick: () => fillForm(null) })
    ]);

    form.appendChild(fieldsWrap);
    form.appendChild(actions);

    form.onsubmit = async (e) => {
      e.preventDefault();
      await save();
    };
  }

  function fillForm(item) {
    for (const f of config.fields) {
      writeValue(f, form, item ? item[f.name] : (f.defaultValue ?? null));
    }
  }

  function startCreate() {
    state.editingId = null;
    renderForm();
    fillForm(null);
  }

  function startEdit(id) {
    const item = state.items.find((x) => (x[config.idField] + '') === (id + ''));
    state.editingId = id;
    renderForm();
    fillForm(item || null);
  }

  async function save() {
    const payload = {};
    for (const f of config.fields) {
      if (f.hidden) continue;
      payload[f.name] = readValue(f, form);
    }

    try {
      if (!state.editingId) {
        await apiFetch(config.createUrl, {
          method: 'POST',
          body: JSON.stringify(payload)
        });
        window.mdmToast?.('Created');
      } else {
        await apiFetch(config.updateUrl(state.editingId), {
          method: 'PUT',
          body: JSON.stringify(payload)
        });
        window.mdmToast?.('Saved');
      }
      await load();
      startCreate();
    } catch (e) {
      window.mdmToast?.(`Error: ${e.message}`);
      throw e;
    }
  }

  async function del(id) {
    const confirmed = await confirmDanger(`Delete ${config.title} #${id}?`);
    if (!confirmed) return;
    try {
      await apiFetch(config.deleteUrl(id), { method: 'DELETE' });
      window.mdmToast?.('Deleted');
      await load();
    } catch (e) {
      window.mdmToast?.(`Error: ${e.message}`);
      throw e;
    }
  }

  async function load() {
    const data = await apiFetch(config.listUrl);
    state.items = Array.isArray(data) ? data : (data?.items || []);
    renderTable();
  }

  renderForm();
  startCreate();
  load().catch((e) => window.mdmToast?.(`Error: ${e.message}`));
}
