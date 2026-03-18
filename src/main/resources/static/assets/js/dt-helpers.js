// Get API version from meta tag (set by Spring Boot property)
const API_VERSION = document.querySelector('meta[name="api-version"]')?.content || 'v1';

// Helper to build versioned API URLs (replaces any existing version with current version)
function apiPath(path) {
  if (!path) return path;
  // Remove any existing version prefix (e.g., /v1/, /v2/) and replace with current version
  const unversionedPath = path.replace(/^\/v\d+\//, '/');
  return `/${API_VERSION}${unversionedPath}`;
}

(() => {
  const ALLOWED_RENDER_TAGS = new Set([
    'A',
    'BUTTON',
    'BR',
    'CODE',
    'DIV',
    'EM',
    'SMALL',
    'SPAN',
    'STRONG'
  ]);
  const ALLOWED_RENDER_ATTRS = new Set([
    'aria-label',
    'class',
    'data-act',
    'data-id',
    'href',
    'rel',
    'role',
    'target',
    'title',
    'type'
  ]);

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function isAllowedHref(value) {
    const href = String(value || '').trim().toLowerCase();
    return href === '' || href.startsWith('#') || href.startsWith('/') || href.startsWith('http://') || href.startsWith('https://');
  }

  function sanitizeRenderHtml(value) {
    const template = document.createElement('template');
    template.innerHTML = String(value ?? '');

    const walker = document.createTreeWalker(template.content, NodeFilter.SHOW_ELEMENT);
    const nodes = [];
    while (walker.nextNode()) {
      nodes.push(walker.currentNode);
    }

    nodes.forEach((node) => {
      const tagName = (node.tagName || '').toUpperCase();
      if (!ALLOWED_RENDER_TAGS.has(tagName)) {
        node.replaceWith(document.createTextNode(node.textContent || ''));
        return;
      }

      Array.from(node.attributes).forEach((attr) => {
        const name = attr.name.toLowerCase();
        const attrValue = attr.value || '';
        const allowed = ALLOWED_RENDER_ATTRS.has(name) && !name.startsWith('on');
        if (!allowed) {
          node.removeAttribute(attr.name);
          return;
        }
        if (name === 'href' && !isAllowedHref(attrValue)) {
          node.removeAttribute(attr.name);
          return;
        }
      });

      if (tagName === 'A' && (node.getAttribute('target') || '').toLowerCase() === '_blank') {
        node.setAttribute('rel', 'noopener noreferrer');
      }
    });

    return template.content;
  }

  function renderCellContent(td, renderedValue) {
    td.textContent = '';
    if (renderedValue == null) {
      return;
    }
    if (renderedValue instanceof Node) {
      td.appendChild(renderedValue);
      return;
    }
    if (typeof renderedValue === 'number' || typeof renderedValue === 'boolean') {
      td.textContent = String(renderedValue);
      return;
    }
    const fragment = sanitizeRenderHtml(renderedValue);
    if (!fragment.childNodes.length) {
      td.textContent = '';
      return;
    }
    td.appendChild(fragment);
  }

  function ensureResponsiveTableWrap(table) {
    if (!(table instanceof HTMLTableElement)) return table;
    const currentParent = table.parentElement;
    if (currentParent && currentParent.classList.contains('table-scroll-wrap')) {
      table.classList.add('table--responsive');
      return currentParent;
    }
    const wrap = document.createElement('div');
    wrap.className = 'table-scroll-wrap';
    table.classList.add('table--responsive');
    table.insertAdjacentElement('beforebegin', wrap);
    wrap.appendChild(table);
    return wrap;
  }

  function createControlBar(container, state, onReload) {
    const bar = document.createElement('div');
    bar.className = 'table-controls';

    const left = document.createElement('div');
    left.className = 'table-controls__left';

    const pageSizeLabel = document.createElement('label');
    pageSizeLabel.className = 'muted';
    pageSizeLabel.textContent = 'Rows:';

    const pageSizeSelect = document.createElement('select');
    [10, 25, 50, 100].forEach((size) => {
      const opt = document.createElement('option');
      opt.value = String(size);
      opt.textContent = String(size);
      if (size === state.length) opt.selected = true;
      pageSizeSelect.appendChild(opt);
    });
    pageSizeSelect.addEventListener('change', () => {
      state.length = Number(pageSizeSelect.value);
      state.start = 0;
      onReload();
    });

    left.appendChild(pageSizeLabel);
    left.appendChild(pageSizeSelect);

    const right = document.createElement('div');
    right.className = 'table-controls__right';

    const searchInput = document.createElement('input');
    searchInput.type = 'text';
    searchInput.placeholder = 'Search...';
    searchInput.value = state.search || '';
    let searchTimeout = null;
    searchInput.addEventListener('input', () => {
      clearTimeout(searchTimeout);
      searchTimeout = setTimeout(() => {
        state.search = searchInput.value.trim();
        state.start = 0;
        onReload();
      }, 250);
    });

    right.appendChild(searchInput);
    bar.appendChild(left);
    bar.appendChild(right);
    container.appendChild(bar);

    state._searchInput = searchInput;
    state._pageSizeSelect = pageSizeSelect;
  }

  function createPager(container, state, onPageChange) {
    container.innerHTML = '';
    const wrapper = document.createElement('div');
    wrapper.className = 'table-pager';

    const info = document.createElement('div');
    info.className = 'muted';
    const start = state.total === 0 ? 0 : state.start + 1;
    const end = Math.min(state.start + state.length, state.total);
    info.textContent = state.total === 0 ? 'No rows.' : `Showing ${start}-${end} of ${state.total}`;

    const controls = document.createElement('div');
    controls.className = 'form-row';

    const prev = document.createElement('button');
    prev.type = 'button';
    prev.className = 'secondary';
    prev.textContent = 'Previous';
    prev.dataset.iconAction = 'previous';
    prev.dataset.iconLabel = 'Previous';
    prev.setAttribute('aria-label', 'Previous page');
    prev.setAttribute('title', 'Previous page');
    prev.disabled = state.start <= 0;
    prev.addEventListener('click', () => {
      if (state.start <= 0) return;
      state.start = Math.max(0, state.start - state.length);
      onPageChange();
    });

    const next = document.createElement('button');
    next.type = 'button';
    next.className = 'secondary';
    next.textContent = 'Next';
    next.dataset.iconAction = 'next';
    next.dataset.iconLabel = 'Next';
    next.setAttribute('aria-label', 'Next page');
    next.setAttribute('title', 'Next page');
    next.disabled = state.start + state.length >= state.total;
    next.addEventListener('click', () => {
      if (state.start + state.length >= state.total) return;
      state.start += state.length;
      onPageChange();
    });

    controls.appendChild(prev);
    controls.appendChild(next);
    wrapper.appendChild(info);
    wrapper.appendChild(controls);
    container.appendChild(wrapper);
  }

  function applyHeaderSorting(table, opts, state, onReload) {
    const headers = Array.from(table.querySelectorAll('thead th'));
    const sortableHeaders = [];

    function updateAriaSort() {
      sortableHeaders.forEach(({ th, col }) => {
        if (state.sortBy !== col.data) {
          th.setAttribute('aria-sort', 'none');
          th.dataset.sortDir = '';
          return;
        }
        const dir = state.sortDir === 'asc' ? 'ascending' : 'descending';
        th.setAttribute('aria-sort', dir);
        th.dataset.sortDir = state.sortDir;
      });
    }

    headers.forEach((th, idx) => {
      const col = (opts.columns || [])[idx];
      if (!col || !col.data || col.orderable === false) return;
      th.classList.add('sortable');
      th.style.cursor = 'pointer';
      th.tabIndex = 0;

      const triggerSort = () => {
        if (state.sortBy === col.data) {
          state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
        } else {
          state.sortBy = col.data;
          state.sortDir = 'asc';
        }
        state.start = 0;
        updateAriaSort();
        onReload();
      };

      th.addEventListener('click', triggerSort);
      th.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') return;
        event.preventDefault();
        triggerSort();
      });
      sortableHeaders.push({ th, col });
    });

    updateAriaSort();
  }

  window.mdmInitDataTable = (selector, opts) => {
    const table = document.querySelector(selector);
    if (!table) throw new Error(`Table not found: ${selector}`);

    let tbody = table.tBodies[0];
    if (!tbody) {
      tbody = document.createElement('tbody');
      table.appendChild(tbody);
    }

    const tableWrap = ensureResponsiveTableWrap(table);

    const controlsContainer = document.createElement('div');
    tableWrap.insertAdjacentElement('beforebegin', controlsContainer);

    const pager = document.createElement('div');
    pager.style.marginTop = '0.5rem';
    tableWrap.insertAdjacentElement('afterend', pager);

    const state = {
      draw: 1,
      start: 0,
      length: opts.pageLength || 10,
      total: 0,
      data: [],
      search: '',
      sortBy: opts.defaultSortBy || null,
      sortDir: opts.defaultSortDir || 'desc',
      requestSeq: 0,
      inFlightController: null
    };

    async function load() {
      const requestId = ++state.requestSeq;
      if (state.inFlightController) {
        state.inFlightController.abort();
      }
      const controller = new AbortController();
      state.inFlightController = controller;
      table.setAttribute('aria-busy', 'true');

      const baseUrl = opts.ajax && opts.ajax.url ? apiPath(opts.ajax.url) : '';
      const url = new URL(baseUrl, window.location.origin);
      url.searchParams.set('draw', String(state.draw++));
      url.searchParams.set('start', String(state.start));
      url.searchParams.set('length', String(state.length));
      if (state.search) url.searchParams.set('search', state.search);
      if (state.sortBy) url.searchParams.set('sort_by', state.sortBy);
      if (state.sortDir) url.searchParams.set('sort_dir', state.sortDir);

      const extraParams = typeof opts.extraParams === 'function' ? opts.extraParams() : null;
      if (extraParams && typeof extraParams === 'object') {
        Object.entries(extraParams).forEach(([k, v]) => {
          if (v !== null && v !== undefined && String(v).trim() !== '') {
            url.searchParams.set(k, String(v));
          }
        });
      }

      const dynamicHeaders = typeof opts.requestHeaders === 'function'
        ? opts.requestHeaders()
        : opts.requestHeaders;
      const resolvedHeaders = (dynamicHeaders && typeof dynamicHeaders === 'object')
        ? dynamicHeaders
        : {};

      try {
        const fetchWithRefresh = typeof window.mdmFetchWithRefresh === 'function'
          ? window.mdmFetchWithRefresh
          : fetch;
        const res = await fetchWithRefresh(url.toString(), {
          headers: {
            Accept: 'application/json',
            ...resolvedHeaders
          },
          credentials: 'same-origin',
          signal: controller.signal
        });
        if (!res.ok) {
          let details = '';
          try {
            const text = await res.text();
            details = text ? `: ${text}` : '';
          } catch {
            details = '';
          }
          throw new Error(`${res.status} ${res.statusText}${details}`);
        }
        const json = await res.json();
        if (requestId !== state.requestSeq) {
          return;
        }
        state.data = json.data || [];
        state.total = Number(json.recordsTotal ?? json.records_total ?? state.data.length);
        renderBody();
        createPager(pager, state, load);
      } catch (error) {
        if (error && error.name === 'AbortError') {
          return;
        }
        throw error;
      } finally {
        if (state.inFlightController === controller) {
          state.inFlightController = null;
        }
        if (requestId === state.requestSeq) {
          table.setAttribute('aria-busy', 'false');
        }
      }
    }

    function renderBody() {
      tbody.innerHTML = '';
      if (!state.data.length) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = Math.max((opts.columns || []).length, 1);
        td.className = 'muted';
        td.textContent = 'No rows.';
        tr.appendChild(td);
        tbody.appendChild(tr);
        return;
      }

        state.data.forEach((row, idx) => {
          const tr = document.createElement('tr');
          tr.dataset.index = String(idx);
          (opts.columns || []).forEach((col) => {
            const td = document.createElement('td');
            if (typeof col.render === 'function') {
              const rendered = col.render(row[col.data], null, row, { escapeHtml });
              renderCellContent(td, rendered);
            } else if (col.data) {
              const v = row[col.data];
              td.textContent = v == null ? '' : String(v);
          }
          tr.appendChild(td);
        });
        tbody.appendChild(tr);
      });
    }

    createControlBar(controlsContainer, state, () => load().catch((e) => window.mdmToast?.(`Table load failed: ${e.message}`)));
    applyHeaderSorting(table, opts, state, () => load().catch((e) => window.mdmToast?.(`Table load failed: ${e.message}`)));
    load().catch((e) => window.mdmToast?.(`Table load failed: ${e.message}`));

    return {
      row: (tr) => ({
        data: () => {
          const idx = Number(tr && tr.dataset ? tr.dataset.index : -1);
          if (Number.isNaN(idx) || idx < 0 || idx >= state.data.length) return null;
          return state.data[idx];
        }
      }),
      ajax: {
        reload: () => load(),
      },
      state
    };
  };

  window.mdmEscapeHtml = escapeHtml;
})();
