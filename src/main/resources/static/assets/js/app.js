(() => {
  const toast = document.createElement('div');
  toast.className = 'toast';
  toast.id = 'toast';
  toast.setAttribute('role', 'status');
  toast.setAttribute('aria-live', 'polite');
  toast.setAttribute('aria-atomic', 'true');
  const confirmState = createConfirmState();
  let uiEnhancementObserver = null;
  let searchableSelectSyncTimer = null;
  let selectOutsideClickBound = false;
  let closeHeaderUserMenu = null;
  let headerStructureObserver = null;
  let headerStructureSyncTimer = null;
  let sessionRefreshInFlight = null;
  let sessionKeepAliveTimer = null;
  let sessionKeepAliveVisibilityBound = false;
  const SESSION_REFRESH_INTERVAL_MS = 30 * 60 * 1000;
  const enhancedSelectState = new Map();
  const iconSvgs = {
    edit: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/></svg>',
    delete: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><polyline points="3 6 5 6 21 6"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/></svg>',
    save: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>',
    reset: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>',
    refresh: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10"/><path d="M20.49 15a9 9 0 0 1-14.85 3.36L1 14"/></svg>',
    copy: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>',
    rotate: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M2.5 12a9.5 9.5 0 1 0 9.5-9.5"/><polyline points="2 4 2 10 8 10"/></svg>',
    open: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M14 3h7v7"/><path d="M10 14L21 3"/><path d="M21 14v7h-7"/><path d="M3 10V3h7"/><path d="M3 21l8-8"/></svg>',
    inspect: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.35-4.35"/></svg>',
    filter: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><polygon points="3 4 21 4 14 12 14 19 10 21 10 12 3 4"/></svg>',
    send: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>',
    update: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M21 12a9 9 0 1 1-2.64-6.36"/><polyline points="21 3 21 9 15 9"/></svg>',
    logout: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>',
    menu: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>',
    view: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"/><circle cx="12" cy="12" r="3"/></svg>',
    create: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>',
    search: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.35-4.35"/></svg>',
    previous: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><polyline points="15 18 9 12 15 6"/></svg>',
    next: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><polyline points="9 18 15 12 9 6"/></svg>',
    confirm: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><polyline points="20 6 9 17 4 12"/></svg>',
    cancel: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>',
    action: '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="9"/></svg>'
  };
  const actionLabels = {
    edit: 'Edit',
    delete: 'Delete',
    save: 'Save',
    reset: 'Reset',
    refresh: 'Refresh',
    copy: 'Copy',
    rotate: 'Rotate',
    open: 'Open',
    inspect: 'Inspect',
    filter: 'Filter',
    send: 'Send',
    update: 'Update',
    logout: 'Logout',
    menu: 'Menu',
    view: 'View detail',
    create: 'Create',
    search: 'Search',
    previous: 'Previous',
    next: 'Next',
    confirm: 'Confirm',
    cancel: 'Cancel',
    action: 'Action'
  };
  const buttonActionByAct = {
    edit: 'edit',
    del: 'delete',
    delete: 'delete',
    clone: 'copy',
    conditions: 'open',
    'view-detail': 'view',
    view: 'view'
  };
  const buttonActionById = {
    resetbtn: 'reset',
    refreshtypes: 'refresh',
    reloadtenantkeytenants: 'refresh',
    rotatetenantkeybtn: 'rotate',
    copytenantgeneratedkey: 'copy',
    logoutbtn: 'logout',
    menutogglebtn: 'menu',
    'refresh-device-detail': 'refresh'
  };
  const buttonActionByText = {
    edit: 'edit',
    delete: 'delete',
    remove: 'delete',
    clone: 'copy',
    conditions: 'open',
    'open conditions': 'open',
    'clone to tenant': 'copy',
    save: 'save',
    reset: 'reset',
    refresh: 'refresh',
    reload: 'refresh',
    copy: 'copy',
    'generate rotate key': 'rotate',
    'rotate key': 'rotate',
    open: 'open',
    'inspect device': 'inspect',
    'apply filters': 'filter',
    'send payload': 'send',
    'update password': 'update',
    logout: 'logout',
    menu: 'menu',
    create: 'create',
    new: 'create',
    search: 'search',
    prev: 'previous',
    previous: 'previous',
    next: 'next',
    confirm: 'confirm',
    cancel: 'cancel',
    'view detail': 'view'
  };

  document.addEventListener('DOMContentLoaded', async () => {
    document.body.appendChild(toast);
    ensureHeaderUserMenuStructure();
    startHeaderStructureGuard();
    initConfirmDialog();
    initMenuDrawer();
    initNavDropdownMenus();
    await initHeaderAuth().catch(() => {});
    initUiEnhancements();
    observeUiEnhancements();
  });

  window.mdmToast = (message) => {
    const el = document.getElementById('toast') || toast;
    el.textContent = message;
    el.dataset.open = 'true';
    clearTimeout(el.__t);
    el.__t = setTimeout(() => {
      el.dataset.open = 'false';
    }, 3500);
  };

  window.mdmConfirm = (options) => openConfirmDialog(options);
  window.mdmUiConfirm = (options) => openConfirmDialog(options);
  window.mdmFetchWithRefresh = (url, options = {}) => fetchWithRefresh(url, options);
  window.mdmRefreshSession = () => refreshSession();

  function normalizeHeaderText(value) {
    return String(value || '').trim().replace(/\s+/g, ' ').toLowerCase();
  }

  function isHeaderChangePasswordLink(node) {
    if (!(node instanceof HTMLAnchorElement)) return false;
    const href = String(node.getAttribute('href') || '');
    if (/\/ui\/change-password(?:$|[?#])/.test(href)) return true;
    return normalizeHeaderText(node.textContent).includes('change password');
  }

  function isHeaderLogoutControl(node) {
    if (!(node instanceof HTMLElement)) return false;
    if (!(node instanceof HTMLButtonElement || node instanceof HTMLAnchorElement)) return false;
    const signal = normalizeHeaderText([
      node.id || '',
      typeof node.className === 'string' ? node.className : '',
      node.textContent || '',
      node.getAttribute('aria-label') || '',
      node.getAttribute('title') || '',
      node.getAttribute('data-icon-label') || ''
    ].join(' '));
    if (signal.includes('logout')) return true;
    if (node instanceof HTMLAnchorElement) {
      const href = String(node.getAttribute('href') || '');
      if (/\/auth\/logout(?:$|[?#])/.test(href)) return true;
    }
    return false;
  }

  function removeDuplicateHeaderIds(headerRoot, id) {
    if (!(headerRoot instanceof HTMLElement) || !id) return;
    const matches = Array.from(headerRoot.querySelectorAll(`#${id}`));
    if (matches.length < 2) return;
    matches.slice(1).forEach((node) => node.remove());
  }

  function startHeaderStructureGuard() {
    const headerRoot = document.querySelector('header');
    const headerRight = document.querySelector('header .header-right') || headerRoot;
    if (!(headerRight instanceof HTMLElement)) return;

    const scheduleSync = () => {
      if (headerStructureSyncTimer) {
        window.clearTimeout(headerStructureSyncTimer);
      }
      headerStructureSyncTimer = window.setTimeout(() => {
        headerStructureSyncTimer = null;
        ensureHeaderUserMenuStructure();
      }, 0);
    };

    if (!headerStructureObserver) {
      headerStructureObserver = new MutationObserver(() => scheduleSync());
      headerStructureObserver.observe(headerRight, {
        childList: true,
        subtree: true
      });
    }

    [0, 120, 360, 900, 1800].forEach((delay) => {
      window.setTimeout(() => ensureHeaderUserMenuStructure(), delay);
    });
  }

  function ensureHeaderUserMenuStructure() {
    const headerRoot = document.querySelector('header');
    const headerRight = document.querySelector('header .header-right') || headerRoot;
    if (!(headerRight instanceof HTMLElement)) return;

    const nav = document.getElementById('primaryNav') || headerRight.querySelector('nav') || headerRoot?.querySelector('nav');
    let trigger = document.getElementById('headerUser');
    let wrap = document.getElementById('headerUserMenuWrap');
    let panel = document.getElementById('headerUserMenu');
    let logoutBtn = document.getElementById('logoutBtn');

    ['headerUser', 'headerUserMenuWrap', 'headerUserMenu', 'logoutBtn'].forEach((id) => {
      removeDuplicateHeaderIds(headerRight, id);
    });

    if (trigger && trigger.tagName !== 'BUTTON') {
      const button = document.createElement('button');
      button.type = 'button';
      button.id = 'headerUser';
      button.className = `${trigger.className || 'user-chip'} user-menu-trigger`;
      button.hidden = Boolean(trigger.hidden);
      button.innerHTML = trigger.innerHTML;
      button.setAttribute('data-no-icon', 'true');
      button.setAttribute('aria-haspopup', 'menu');
      button.setAttribute('aria-expanded', 'false');
      button.setAttribute('aria-controls', 'headerUserMenu');
      trigger.replaceWith(button);
      trigger = button;
    }

    if (trigger instanceof HTMLButtonElement) {
      trigger.type = 'button';
      trigger.classList.add('user-menu-trigger');
      trigger.setAttribute('data-no-icon', 'true');
      trigger.setAttribute('aria-haspopup', 'menu');
      trigger.setAttribute('aria-expanded', 'false');
      trigger.setAttribute('aria-controls', 'headerUserMenu');
    }

    if (!wrap && trigger) {
      wrap = document.createElement('div');
      wrap.id = 'headerUserMenuWrap';
      wrap.className = 'header-user-menu';
      wrap.hidden = true;
      headerRight.appendChild(wrap);
      wrap.appendChild(trigger);
    }
    if (wrap && trigger && trigger.parentElement !== wrap) {
      wrap.appendChild(trigger);
    }

    if (!panel && wrap) {
      panel = document.createElement('div');
      panel.id = 'headerUserMenu';
      panel.className = 'header-user-menu__panel';
      panel.setAttribute('role', 'menu');
      panel.hidden = true;
      wrap.appendChild(panel);
    }

    const navChangePasswordLinks = nav ? Array.from(nav.querySelectorAll('a')).filter((link) => isHeaderChangePasswordLink(link)) : [];
    navChangePasswordLinks.forEach((link) => link.remove());
    Array.from(headerRight.querySelectorAll('a'))
      .filter((link) => isHeaderChangePasswordLink(link) && !(panel && panel.contains(link)))
      .forEach((link) => link.remove());

    if (panel && !panel.querySelector('a[href$="/ui/change-password"]')) {
      const link = document.createElement('a');
      link.href = '/ui/change-password';
      link.textContent = 'Change Password';
      link.setAttribute('role', 'menuitem');
      link.setAttribute('data-drawer-close', 'true');
      panel.appendChild(link);
    }

    const headerLogoutControls = Array.from(headerRight.querySelectorAll('button, a'))
      .filter((node) => {
        if (!(node instanceof HTMLElement)) return false;
        if (node === trigger) return false;
        if (panel && panel.contains(node)) return false;
        return isHeaderLogoutControl(node);
      });
    let adoptedLegacyLogoutBtn = null;
    if (!logoutBtn && headerLogoutControls.length > 0) {
      const first = headerLogoutControls.shift();
      if (first instanceof HTMLButtonElement) {
        adoptedLegacyLogoutBtn = first;
      } else if (first instanceof HTMLAnchorElement) {
        first.remove();
      }
    }
    headerLogoutControls.forEach((node) => node.remove());
    if (!logoutBtn && adoptedLegacyLogoutBtn) {
      logoutBtn = adoptedLegacyLogoutBtn;
      logoutBtn.id = 'logoutBtn';
      delete logoutBtn.dataset.decorated;
    }

    if (logoutBtn && !(logoutBtn instanceof HTMLButtonElement)) {
      const converted = document.createElement('button');
      converted.id = 'logoutBtn';
      converted.type = 'button';
      converted.textContent = 'Logout';
      logoutBtn.replaceWith(converted);
      logoutBtn = converted;
    }

    if (!logoutBtn && panel) {
      logoutBtn = document.createElement('button');
      logoutBtn.id = 'logoutBtn';
      logoutBtn.type = 'button';
      logoutBtn.className = 'secondary header-user-menu__logout';
      logoutBtn.textContent = 'Logout';
      panel.appendChild(logoutBtn);
    }
    if (logoutBtn && panel && logoutBtn.parentElement !== panel) {
      panel.appendChild(logoutBtn);
    }
    if (logoutBtn instanceof HTMLButtonElement) {
      logoutBtn.classList.add('header-user-menu__logout');
      logoutBtn.setAttribute('data-no-icon', 'true');
      logoutBtn.setAttribute('role', 'menuitem');
      delete logoutBtn.dataset.iconized;
      bindLogoutButton(logoutBtn);
    }

    const duplicatePanelLogoutButtons = panel
      ? Array.from(panel.querySelectorAll('button, a'))
        .filter((node) => node !== logoutBtn && isHeaderLogoutControl(node))
      : [];
    duplicatePanelLogoutButtons.forEach((node) => node.remove());
  }

  async function initHeaderAuth() {
    const headerUser = document.getElementById('headerUser');
    const headerUserMenu = document.getElementById('headerUserMenu');
    const headerUserMenuWrap = document.getElementById('headerUserMenuWrap');
    const logoutBtn = document.getElementById('logoutBtn');
    if (headerUser && headerUserMenu && headerUserMenuWrap) {
      initHeaderUserMenu(headerUser, headerUserMenu, headerUserMenuWrap);
    }

    try {
      const me = await loadAuthenticatedUser();
      if (me && me.authenticated) {
        renderHeaderUser(headerUser, me);
        if (headerUserMenuWrap) {
          headerUserMenuWrap.hidden = false;
        }
        startSessionKeepAlive();
      } else if (headerUser) {
        headerUser.hidden = true;
        if (headerUserMenuWrap) {
          headerUserMenuWrap.hidden = true;
        }
        stopSessionKeepAlive();
      }
    } catch (error) {
      if (headerUser) {
        headerUser.hidden = true;
      }
      if (headerUserMenuWrap) {
        headerUserMenuWrap.hidden = true;
      }
      stopSessionKeepAlive();
    }

    bindLogoutButton(logoutBtn);
  }

  function bindLogoutButton(logoutBtn) {
    if (!(logoutBtn instanceof HTMLButtonElement)) return;
    if (logoutBtn.dataset.logoutBound === 'true') return;
    logoutBtn.dataset.logoutBound = 'true';
    logoutBtn.addEventListener('click', async () => {
      logoutBtn.disabled = true;
      try {
        await performLogout();
      } finally {
        logoutBtn.disabled = false;
      }
    });
  }

  async function performLogout() {
    try {
      const headers = {};
      const csrfToken = readCookie('XSRF-TOKEN');
      if (csrfToken) headers['X-XSRF-TOKEN'] = csrfToken;
      await fetch('/auth/logout', { method: 'POST', credentials: 'same-origin', headers });
    } catch (error) {
    } finally {
      stopSessionKeepAlive();
      window.location.href = '/login';
    }
  }

  function initHeaderUserMenu(trigger, panel, wrap) {
    if (!trigger || !panel || !wrap) return;
    if (trigger.dataset.menuBound === 'true') return;
    trigger.dataset.menuBound = 'true';
    wrap.dataset.open = 'false';
    panel.setAttribute('aria-hidden', 'true');

    const closeMenu = (restoreFocus = false) => {
      if (wrap.dataset.open !== 'true') return;
      wrap.dataset.open = 'false';
      panel.hidden = true;
      panel.setAttribute('aria-hidden', 'true');
      trigger.setAttribute('aria-expanded', 'false');
      if (restoreFocus) {
        trigger.focus();
      }
    };

    const openMenu = () => {
      if (trigger.hidden) return;
      wrap.dataset.open = 'true';
      panel.hidden = false;
      panel.setAttribute('aria-hidden', 'false');
      trigger.setAttribute('aria-expanded', 'true');
      const first = panel.querySelector('a[href], button:not([disabled])');
      if (first instanceof HTMLElement) {
        first.focus();
      }
    };

    closeHeaderUserMenu = closeMenu;
    decorateHeaderUserMenu(panel);

    trigger.addEventListener('click', (event) => {
      event.preventDefault();
      if (wrap.dataset.open === 'true') {
        closeMenu(false);
      } else {
        openMenu();
      }
    });

    trigger.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ' && event.key !== 'ArrowDown') return;
      event.preventDefault();
      openMenu();
    });

    panel.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeMenu(true);
      }
    });

    document.addEventListener('click', (event) => {
      if (!wrap.contains(event.target)) {
        closeMenu(false);
      }
    });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape') return;
      closeMenu(true);
    });

    panel.addEventListener('click', (event) => {
      const menuItem = event.target.closest('a,button');
      if (!menuItem) return;
      closeMenu(false);
    });
  }

  function decorateHeaderUserMenu(panel) {
    if (!panel) return;
    const logoutButton = panel.querySelector('#logoutBtn');
    if (!(logoutButton instanceof HTMLButtonElement)) return;
    if (logoutButton.dataset.decorated === 'true') return;
    const label = 'Logout';
    const icon = iconSvgs.logout || '';
    logoutButton.dataset.decorated = 'true';
    logoutButton.setAttribute('aria-label', label);
    logoutButton.setAttribute('title', label);
    logoutButton.innerHTML = `<span class="header-user-menu__icon" aria-hidden="true">${icon}</span><span>${label}</span>`;
  }

  async function loadAuthenticatedUser() {
    const response = await fetchWithRefresh('/auth/me', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    });
    if (!response.ok) return null;
    const me = await response.json().catch(() => null);
    return me && me.authenticated ? me : null;
  }

  function renderHeaderUser(headerUser, me) {
    if (!headerUser) return;

    const username = (me.username || me.user_name || 'User').toString();
    const role = (me.role || 'USER').toString();
    const tenantId = me.tenantId ?? me.tenant_id;
    const tenantLabel = tenantId == null ? 'GLOBAL' : `TENANT ${tenantId}`;

    const roleClass = role === 'PRODUCT_ADMIN' ? 'user-chip--admin' : 'user-chip--tenant';
    headerUser.className = `user-chip user-menu-trigger ${roleClass}`;
    headerUser.textContent = '';
    const nameEl = document.createElement('span');
    nameEl.className = 'user-chip__name';
    nameEl.textContent = username;
    const metaEl = document.createElement('span');
    metaEl.className = 'user-chip__meta';
    metaEl.textContent = `${role} | ${tenantLabel}`;
    headerUser.appendChild(nameEl);
    headerUser.appendChild(metaEl);
    headerUser.hidden = false;
  }

  function initMenuDrawer() {
    const toggleBtn = document.getElementById('menuToggleBtn');
    const backdrop = document.getElementById('navBackdrop');
    const nav = document.getElementById('primaryNav');
    const drawer = document.getElementById('primaryNavShell') || (nav ? nav.closest('.header-right') : null);
    if (!toggleBtn || !nav || !drawer) return;

    const focusableSelector = 'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])';
    let lastFocusedElement = null;
    const isMobile = () => window.innerWidth <= 960;

    const isDrawerOpen = () => document.body.getAttribute('data-nav-open') === 'true';
    const isDrawerCollapsed = () => document.body.getAttribute('data-nav-collapsed') === 'true';

    const getFocusableElements = () => Array.from(drawer.querySelectorAll(focusableSelector))
      .filter((node) => !node.hasAttribute('disabled') && !node.hidden);

    toggleBtn.setAttribute('aria-label', 'Toggle menu');
    drawer.tabIndex = -1;
    backdrop?.setAttribute('aria-hidden', 'true');

    const closeMobile = () => {
      if (!isDrawerOpen()) return;
      document.body.removeAttribute('data-nav-open');
      backdrop?.setAttribute('aria-hidden', 'true');
      closeNavDropdownMenus();
      if (typeof closeHeaderUserMenu === 'function') {
        closeHeaderUserMenu(false);
      }
      updateBodyScrollLock();
      if (lastFocusedElement && typeof lastFocusedElement.focus === 'function') {
        lastFocusedElement.focus();
      } else {
        toggleBtn.focus();
      }
    };

    const openMobile = () => {
      lastFocusedElement = document.activeElement;
      document.body.setAttribute('data-nav-open', 'true');
      backdrop?.setAttribute('aria-hidden', 'false');
      updateBodyScrollLock();
      const focusables = getFocusableElements();
      if (focusables.length) {
        focusables[0].focus();
      } else {
        drawer.focus();
      }
    };

    const applyDesktopCollapsedState = (collapsed) => {
      if (collapsed) {
        document.body.setAttribute('data-nav-collapsed', 'true');
      } else {
        document.body.removeAttribute('data-nav-collapsed');
      }
    };

    const syncDrawerA11yState = () => {
      if (isMobile()) {
        const open = isDrawerOpen();
        toggleBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
        drawer.setAttribute('aria-hidden', open ? 'false' : 'true');
        if (!open) {
          backdrop?.setAttribute('aria-hidden', 'true');
        }
        return;
      }

      const collapsed = isDrawerCollapsed();
      document.body.removeAttribute('data-nav-open');
      backdrop?.setAttribute('aria-hidden', 'true');
      toggleBtn.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
      drawer.setAttribute('aria-hidden', collapsed ? 'true' : 'false');
      updateBodyScrollLock();
    };

    toggleBtn.addEventListener('click', () => {
      if (isMobile()) {
        const open = isDrawerOpen();
        if (open) {
          closeMobile();
        } else {
          openMobile();
        }
        syncDrawerA11yState();
        return;
      }

      applyDesktopCollapsedState(!isDrawerCollapsed());
      closeNavDropdownMenus();
      if (typeof closeHeaderUserMenu === 'function') {
        closeHeaderUserMenu(false);
      }
      syncDrawerA11yState();
    });

    backdrop?.addEventListener('click', () => {
      closeMobile();
      syncDrawerA11yState();
    });
    nav.addEventListener('click', (e) => {
      if (isMobile() && e.target.closest('a')) {
        closeMobile();
        syncDrawerA11yState();
      }
    });
    drawer.addEventListener('click', (e) => {
      if (isMobile() && e.target.closest('[data-drawer-close="true"]')) {
        closeMobile();
        syncDrawerA11yState();
      }
    });
    window.addEventListener('resize', () => {
      if (!isMobile()) {
        closeMobile();
      }
      syncDrawerA11yState();
    });

    document.addEventListener('keydown', (event) => {
      if (!isDrawerOpen()) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        closeMobile();
        syncDrawerA11yState();
        return;
      }
      if (event.key !== 'Tab') return;

      const focusables = getFocusableElements();
      if (!focusables.length) {
        event.preventDefault();
        return;
      }

      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    });

    syncDrawerA11yState();
  }

  function closeNavDropdownMenus() {
    document.querySelectorAll('#primaryNav .nav-dropdown[open]').forEach((item) => {
      item.open = false;
    });
  }

  function initNavDropdownMenus() {
    const dropdowns = Array.from(document.querySelectorAll('#primaryNav .nav-dropdown'));
    if (!dropdowns.length) return;

    dropdowns.forEach((dropdown) => {
      if (dropdown.dataset.dropdownBound === 'true') return;
      dropdown.dataset.dropdownBound = 'true';
      const summaryText = String(dropdown.querySelector('summary')?.textContent || '').trim().toLowerCase();
      if (summaryText === 'management') {
        dropdown.querySelectorAll('.nav-dropdown__menu a[href]').forEach((link) => {
          link.setAttribute('target', '_blank');
          link.setAttribute('rel', 'noopener noreferrer');
        });
      }

      dropdown.addEventListener('toggle', () => {
        if (!dropdown.open) return;
        dropdowns.forEach((other) => {
          if (other !== dropdown) {
            other.open = false;
          }
        });
      });

      dropdown.addEventListener('click', (event) => {
        const link = event.target.closest('a[href]');
        if (link) {
          dropdown.open = false;
        }
      });
    });

    document.addEventListener('click', (event) => {
      dropdowns.forEach((dropdown) => {
        if (dropdown.open && !dropdown.contains(event.target)) {
          dropdown.open = false;
        }
      });
    });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape') return;
      closeNavDropdownMenus();
    });
  }

  function initUiEnhancements() {
    enhanceActionButtons(document);
    enhanceFieldHelpTooltips(document);
    enhanceSearchableSelects(document);
    bindSelectOutsideClickHandler();
    startSearchableSelectSync();
  }

  function observeUiEnhancements() {
    if (uiEnhancementObserver || !document.body) return;
    uiEnhancementObserver = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        if (mutation.type === 'childList') {
          for (const node of mutation.addedNodes) {
            if (!(node instanceof Element)) continue;
            enhanceActionButtons(node);
            enhanceFieldHelpTooltips(node);
            enhanceSearchableSelects(node);
          }
        } else if (mutation.type === 'characterData') {
          const parent = mutation.target.parentElement;
          const button = parent ? parent.closest('button') : null;
          if (button) {
            enhanceActionButtons(button);
          }
        } else if (mutation.type === 'attributes' && mutation.target instanceof HTMLButtonElement) {
          enhanceActionButtons(mutation.target);
        }
      }
    });
    uiEnhancementObserver.observe(document.body, {
      childList: true,
      subtree: true,
      characterData: true,
      attributes: true,
      attributeFilter: ['data-act', 'data-icon-action', 'aria-label', 'title', 'class']
    });
  }

  function normalizeActionText(value) {
    return String(value || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
  }

  const commonFieldHelpCatalog = {
    status: 'Controls whether this record is currently active for evaluation and policy execution.',
    description: 'Business description shown to operators so they understand the purpose of this record.',
    severity: 'Relative impact level used by policy workflows. Recommended scale: 1 Low, 2 Guarded, 3 Medium, 4 High, 5 Critical.',
    priority: 'Execution order when multiple rules are eligible. Lower numbers run first; 100 is a common default.',
    effective_from: 'Pick the effective start date. It is saved as UTC start-of-day timestamp.',
    effective_to: 'Pick the effective end date. It is saved as UTC start-of-day timestamp; keep empty to remain open-ended.',
    os_type: 'Operating system family this record targets, such as ANDROID, IOS, WINDOWS, or LINUX.',
    device_type: 'Device category this record targets, for example PHONE, TABLET, LAPTOP, or DESKTOP.',
    tenant_id: 'Numeric tenant identifier used to scope this record to a tenant context.',
    username: 'Unique login name used to identify the user account.',
    password: 'Credential used to authenticate the user account.'
  };

  const pageFieldHelpCatalog = [
    {
      pattern: '/login',
      fields: {
        username: 'Enter your console login username.',
        password: 'Enter your login password exactly as configured for this user.'
      }
    },
    {
      pattern: '/ui',
      fields: {
        tenantid: 'Optional tenant filter for device inspection. Leave blank to use global context.',
        deviceid: 'External device identifier exactly as sent by the agent payload.'
      }
    },
    {
      pattern: '/ui/devices',
      fields: {
        ostype: 'Filter device table by operating system family.',
        scoreband: 'Filter device table by computed trust score band.',
        detailtenantid: 'Optional tenant override used when loading detail APIs for the selected device.'
      }
    },
    {
      pattern: '/ui/payloads',
      fields: {
        tenantid: 'Tenant ID where the test posture payload will be ingested.',
        tenantkey: 'Optional tenant API key header used for tenant-key protected ingestion endpoints.',
        deviceexternalid: 'Device external identifier to associate with the synthetic posture payload.'
      }
    },
    {
      pattern: '/ui/change-password',
      fields: {
        currentpassword: 'Current password for verification before allowing a credential change.',
        newpassword: 'New password to set for your user account.',
        confirmpassword: 'Re-enter the new password to confirm there are no typing errors.'
      }
    },
    {
      pattern: '/ui/lookups',
      fields: {
        lookuptype: 'Lookup master category used to scope list values and dropdown options.',
        code: 'Machine-readable code consumed by backend logic and UI dropdown bindings.',
        description: 'Operator-friendly display label for this lookup code.'
      }
    },
    {
      pattern: '/ui/catalog/applications',
      fields: {
        os_type: 'Operating system that this catalog application entry belongs to.',
        package_id: 'Application package identifier used for matching inventory and posture payload data.',
        app_name: 'Display name of the application as shown in policy and catalog screens.',
        publisher: 'Software publisher or vendor name for this application.'
      }
    },
    {
      pattern: '/ui/os-lifecycle',
      fields: {
        platform_code: 'Stable platform identifier used to group lifecycle rows for the same platform.',
        os_type: 'Operating system family for this lifecycle record.',
        os_name: 'Human-readable operating system name.',
        cycle: 'Release train or channel, for example 14, LTS, ESR, or stable.',
        released_on: 'Vendor release date for this cycle.',
        eol_on: 'End-of-life date after which the vendor no longer supports this cycle.',
        eeol_on: 'Extended support end date if the vendor provides paid/extended maintenance.',
        latest_version: 'Latest patch/build currently available within this release cycle.',
        support_state: 'Current lifecycle state such as SUPPORTED, EOL, or EXTENDED_SUPPORT.',
        source_name: 'Source system or feed name used to import this lifecycle data.',
        source_url: 'Reference URL for lifecycle evidence or vendor documentation.',
        notes: 'Additional lifecycle notes, exceptions, or operator remarks.',
        status: 'Enable or disable this lifecycle record for policy evaluation.'
      }
    },
    {
      pattern: '/ui/policies/system-rules',
      fields: {
        rulecodepicker: 'Select a system rule code to open condition management for that specific rule.',
        rule_code: 'Auto-generated unique rule key used by evaluator logic and API integrations.',
        rule_tag: 'Business grouping tag used to classify similar rules in reports and analytics.',
        os_type: 'OS family where this rule should be evaluated.',
        device_type: 'Device class where this rule applies.',
        status: 'Rule activation state. Inactive rules are excluded from evaluator runs.',
        severity: 'Risk severity assigned when this rule matches. Use 1 Low to 5 Critical.',
        priority: 'Execution priority for this rule relative to others. Lower numbers are evaluated first.',
        version: 'Rule version number for controlled policy changes.',
        match_mode: 'Condition evaluation strategy, for example ALL conditions or ANY condition.',
        compliance_action: 'Compliance action emitted when this rule matches.',
        risk_score_delta: 'Trust score adjustment applied when this rule matches. Negative values reduce trust, positive values increase trust.',
        description: 'Operator-focused explanation of the rule purpose and expected trigger behavior.'
      }
    },
    {
      pattern: '/ui/policies/system-rules/*/conditions',
      fields: {
        condition_group: 'Logical group number used to combine condition rows (same group evaluated together).',
        field_name: 'Payload or profile field name evaluated by this condition.',
        operator: 'Comparison operator used to evaluate the field value.',
        status: 'Condition activation state.',
        value_text: 'Text comparison value for string operators.',
        value_numeric: 'Numeric comparison value for number-based operators.',
        value_boolean: 'Boolean value used for true/false condition checks.'
      }
    },
    {
      pattern: '/ui/policies/reject-apps',
      fields: {
        policy_tag: 'Grouping tag used to organize related reject-application rules.',
        threat_type: 'Threat classification used for reporting and remediation routing.',
        severity: 'Risk severity for this app rule. Use 1 Low to 5 Critical. Fallback score impact is max(-80, -10 x severity).',
        blocked_reason: 'User-facing reason explaining why this application is blocked or restricted.',
        app_os_type: 'Operating system where this app policy applies.',
        app_category: 'Application category for reporting and filter use cases.',
        app_name: 'Application display name used for matching and operator visibility.',
        publisher: 'Software vendor/publisher associated with the application.',
        package_id: 'Canonical package identifier used for exact application matching.',
        app_latest_version: 'Latest approved or observed version for this app.',
        min_allowed_version: 'Minimum application version allowed before remediation/block actions.',
        status: 'Policy activation state.',
        effective_from: 'Start timestamp when this policy should begin enforcement.',
        effective_to: 'End timestamp after which this policy should stop enforcement.'
      }
    },
    {
      pattern: '/ui/policies/trust-score-policies',
      fields: {
        policy_code: 'Unique identifier for this trust-score policy.',
        source_type: 'Signal source used to evaluate this scoring policy.',
        signal_key: 'Signal key from payload/evaluation stream to match for this policy.',
        severity: 'Optional severity filter. Leave empty to match all severities for the same source and signal.',
        compliance_action: 'Compliance action associated with this scoring policy.',
        score_delta: 'Base score delta applied when this policy matches. Negative reduces trust; positive increases trust.',
        weight: 'Multiplier for score_delta. Effective delta is round(score_delta x weight), clamped to -1000..1000.',
        status: 'Policy activation state.',
        effective_from: 'Start timestamp for policy applicability.',
        effective_to: 'End timestamp for policy applicability.'
      }
    },
    {
      pattern: '/ui/policies/trust-decision-policies',
      fields: {
        policy_name: 'Human-readable name for this decision policy.',
        score_min: 'Inclusive minimum trust score for this decision range (0..100).',
        score_max: 'Inclusive maximum trust score for this decision range (0..100). Must be >= score_min.',
        decision_action: 'Final decision returned to the agent/platform for this score range.',
        remediation_required: 'Flag indicating whether remediation workflow must be created for this range.',
        response_message: 'Optional message returned with the decision for operator/agent context.',
        status: 'Policy activation state.',
        effective_from: 'Start timestamp when this decision mapping is active.',
        effective_to: 'End timestamp when this decision mapping expires.'
      }
    },
    {
      pattern: '/ui/policies/remediation-rules',
      fields: {
        remediation_code: 'Auto-generated unique remediation rule identifier used across policy mappings.',
        title: 'Short remediation title shown in UI and notifications.',
        description: 'Detailed remediation guidance shown to operators or end users.',
        remediation_type: 'Remediation category defining the expected action model.',
        os_type: 'OS family targeted by this remediation rule.',
        device_type: 'Device class targeted by this remediation rule.',
        priority: 'Execution/display priority when multiple remediations are generated. Lower numbers execute first.',
        status: 'Rule activation state.',
        effective_from: 'Start timestamp when this remediation can be emitted.',
        effective_to: 'End timestamp when this remediation should no longer be emitted.'
      }
    },
    {
      pattern: '/ui/policies/rule-remediation-mappings',
      fields: {
        source_type: 'Source entity type that triggers this mapping (system rule, reject app, trust policy, or decision).',
        system_information_rule_id: 'Select a system rule when source_type is SYSTEM_RULE.',
        reject_application_list_id: 'Select a reject-app policy when source_type is REJECT_APPLICATION.',
        trust_score_policy_id: 'Select a trust-score policy when source_type is TRUST_POLICY.',
        decision_action: 'Decision action to map when source_type is DECISION.',
        remediation_rule_id: 'Select remediation rule that should be created when this mapping matches.',
        enforce_mode: 'Enforcement behavior for this mapping: AUTO, MANUAL, or ADVISORY.',
        rank_order: 'Ordering index used when multiple mappings match the same evaluation context. Lower rank executes first.',
        status: 'Mapping activation state.',
        effective_from: 'Start timestamp when this mapping is valid.',
        effective_to: 'End timestamp when this mapping is no longer valid.'
      }
    },
    {
      pattern: '/ui/tenants',
      fields: {
        tenant_id: 'Numeric tenant identifier that uniquely identifies a tenant.',
        name: 'Tenant display name shown in administration and reporting screens.',
        status: 'Tenant status controlling whether tenant APIs and policy processing remain active.',
        tenantkeytenant: 'Tenant used for API key generation or key rotation actions.'
      }
    },
    {
      pattern: '/ui/users',
      fields: {
        username: 'Login username for this console user.',
        password: 'Initial or updated password for this user account.',
        role: 'Authorization role that controls allowed pages and actions.',
        tenant_id: 'Tenant assignment for tenant-scoped users. Leave empty for global admin users if allowed.',
        status: 'User account activation state.'
      }
    }
  ];

  function normalizeSpaces(value) {
    return String(value || '').replace(/\s+/g, ' ').trim();
  }

  function normalizePathForHelp(pathname) {
    const base = String(pathname || '/').split('?')[0].split('#')[0].trim() || '/';
    if (base.length > 1 && base.endsWith('/')) {
      return base.slice(0, -1);
    }
    return base;
  }

  function pathPatternMatches(pattern, pathname) {
    const normalizedPattern = normalizePathForHelp(pattern);
    const normalizedPath = normalizePathForHelp(pathname);
    const escaped = normalizedPattern
      .replace(/[.+?^${}()|[\]\\]/g, '\\$&')
      .replace(/\*/g, '.*');
    const regex = new RegExp(`^${escaped}$`);
    return regex.test(normalizedPath);
  }

  function lookupMappedHelpByField(fieldKey) {
    const key = normalizeSpaces(fieldKey || '').toLowerCase();
    if (!key) return '';
    const path = normalizePathForHelp(window.location.pathname || '/');

    for (const entry of pageFieldHelpCatalog) {
      if (!entry || !entry.pattern || !entry.fields) continue;
      if (!pathPatternMatches(entry.pattern, path)) continue;
      const value = entry.fields[key];
      if (value) return normalizeSpaces(value);
    }
    return normalizeSpaces(commonFieldHelpCatalog[key] || '');
  }

  function sentenceCase(value) {
    const text = normalizeSpaces(value);
    if (!text) return '';
    return text[0].toUpperCase() + text.slice(1);
  }

  function stripTrailingPeriod(value) {
    return normalizeSpaces(value).replace(/\.+$/, '');
  }

  function fieldLabelText(label, fallback = 'this field') {
    if (!(label instanceof HTMLLabelElement)) return fallback;
    const clone = label.cloneNode(true);
    clone.querySelectorAll('.field-help').forEach((node) => node.remove());
    const text = normalizeSpaces(clone.textContent || '');
    return text || fallback;
  }

  function buildFieldMetaHints(field) {
    const tag = (field.tagName || '').toUpperCase();
    const type = String(field.getAttribute('type') || '').toLowerCase();
    const hints = [];

    if (field.hasAttribute('required')) {
      hints.push('Required');
    } else if (tag !== 'SELECT' || !field.hasAttribute('multiple')) {
      hints.push('Optional');
    }

    const placeholder = normalizeSpaces(field.getAttribute('placeholder') || '');
    if (placeholder) {
      if (/iso[- ]?8601/i.test(placeholder)) {
        hints.push('Use ISO-8601 format');
      } else if (!/^optional$/i.test(placeholder)) {
        hints.push(`Example: ${stripTrailingPeriod(placeholder)}`);
      }
    }

    const min = field.getAttribute('min');
    const max = field.getAttribute('max');
    if (min && max) {
      hints.push(`Allowed range: ${min} to ${max}`);
    } else if (min) {
      hints.push(`Minimum value: ${min}`);
    } else if (max) {
      hints.push(`Maximum value: ${max}`);
    }

    if (type === 'number') {
      const step = field.getAttribute('step');
      if (step && step !== 'any') {
        hints.push(`Step: ${step}`);
      }
    }
    return hints;
  }

  function enrichHelpText(baseText, field) {
    const base = stripTrailingPeriod(baseText);
    const hints = buildFieldMetaHints(field);
    if (!hints.length) return sentenceCase(`${base}.`);
    return sentenceCase(`${base}. ${hints.join('. ')}.`);
  }

  function inferFieldHelpText(field, labelText) {
    const fieldName = String(labelText || 'this field').toLowerCase();
    const tag = (field.tagName || '').toUpperCase();
    const type = String(field.getAttribute('type') || '').toLowerCase();

    let meaning = `Enter ${fieldName}`;
    if (tag === 'SELECT') {
      meaning = `Select ${fieldName} from the available options`;
    } else if (tag === 'TEXTAREA') {
      meaning = `Provide detailed notes for ${fieldName}`;
    } else if (type === 'checkbox') {
      meaning = `Enable or disable ${fieldName}`;
    } else if (type === 'number') {
      meaning = `Enter a numeric value for ${fieldName}`;
    } else if (type === 'password') {
      meaning = `Enter ${fieldName} securely`;
    } else if (type === 'email') {
      meaning = `Enter a valid email address for ${fieldName}`;
    } else if (type === 'date') {
      meaning = `Enter ${fieldName} in YYYY-MM-DD format`;
    } else if (type === 'datetime-local') {
      meaning = `Enter ${fieldName} in YYYY-MM-DDThh:mm format`;
    } else if (type === 'time') {
      meaning = `Enter ${fieldName} in hh:mm format`;
    }

    return enrichHelpText(meaning, field);
  }

  function resolveFieldHelpText(field, label) {
    const explicit = [
      field.getAttribute('data-help'),
      label.getAttribute('data-help'),
      field.getAttribute('aria-description'),
      field.getAttribute('title')
    ].map((value) => normalizeSpaces(value || '')).find((value) => value.length > 0);

    if (explicit) {
      return enrichHelpText(explicit, field);
    }

    const mapped = lookupMappedHelpByField(field.id || field.name || '');
    if (mapped) {
      return enrichHelpText(mapped, field);
    }

    return inferFieldHelpText(field, fieldLabelText(label));
  }

  function isTooltipEligibleField(field) {
    if (!(field instanceof HTMLElement)) return false;
    if (!(field instanceof HTMLInputElement || field instanceof HTMLSelectElement || field instanceof HTMLTextAreaElement)) return false;
    if (field instanceof HTMLInputElement) {
      const type = String(field.type || '').toLowerCase();
      if (['hidden', 'submit', 'button', 'reset', 'image', 'file'].includes(type)) {
        return false;
      }
    }
    return true;
  }

  function attachFieldHelpToLabel(label) {
    if (!(label instanceof HTMLLabelElement)) return;
    if (label.dataset.helpEnhanced === 'true') return;
    const fieldId = normalizeSpaces(label.htmlFor || '');
    if (!fieldId) return;
    const field = document.getElementById(fieldId);
    if (!isTooltipEligibleField(field)) return;

    const helpText = resolveFieldHelpText(field, label);
    if (!helpText) return;

    const hint = document.createElement('span');
    hint.className = 'field-help';
    hint.tabIndex = 0;
    hint.setAttribute('role', 'note');
    hint.setAttribute('aria-label', helpText);
    hint.setAttribute('data-tooltip', helpText);
    hint.removeAttribute('title');

    label.insertAdjacentElement('afterend', hint);
    label.dataset.helpEnhanced = 'true';
  }

  function enhanceFieldHelpTooltips(root) {
    if (root instanceof HTMLLabelElement) {
      attachFieldHelpToLabel(root);
      return;
    }
    if (root.querySelectorAll) {
      root.querySelectorAll('label[for]').forEach((label) => attachFieldHelpToLabel(label));
    }
  }

  function actionLabel(actionType, fallback = '') {
    return actionLabels[actionType] || fallback || 'Action';
  }

  function isIconizationCandidate(button) {
    if (!(button instanceof HTMLButtonElement)) return false;
    if (button.dataset.noIcon === 'true') return false;
    if (button.classList.contains('select2-lite__trigger')) return false;
    if (button.classList.contains('select2-lite__option')) return false;
    if (button.closest('.select2-lite')) return false;
    return true;
  }

  function resolveActionType(button) {
    if (!button || !isIconizationCandidate(button)) return null;

    const explicit = normalizeActionText(button.dataset.iconAction || '');
    if (explicit && iconSvgs[explicit]) return explicit;

    const act = normalizeActionText(button.getAttribute('data-act') || '');
    if (act && buttonActionByAct[act]) {
      return buttonActionByAct[act];
    }

    const id = (button.id || '').trim().toLowerCase();
    if (id && buttonActionById[id]) {
      return buttonActionById[id];
    }
    if (id.includes('refresh') || id.includes('reload')) return 'refresh';
    if (id.includes('reset')) return 'reset';
    if (id.includes('copy')) return 'copy';
    if (id.includes('rotate') || id.includes('key')) return 'rotate';

    const textKey = normalizeActionText(button.textContent || button.dataset.iconLabel || '');
    if (!textKey) return null;
    if (buttonActionByText[textKey]) {
      return buttonActionByText[textKey];
    }

    if (textKey.includes('delete') || textKey.includes('remove')) return 'delete';
    if (textKey.includes('edit')) return 'edit';
    if (textKey.includes('save')) return 'save';
    if (textKey.includes('reset')) return 'reset';
    if (textKey.includes('refresh') || textKey.includes('reload')) return 'refresh';
    if (textKey.includes('copy')) return 'copy';
    if (textKey.includes('rotate') || textKey.includes('generate') || textKey.includes('key')) return 'rotate';
    if (textKey.includes('open')) return 'open';
    if (textKey.includes('inspect')) return 'inspect';
    if (textKey.includes('filter') || textKey.includes('apply')) return 'filter';
    if (textKey.includes('send')) return 'send';
    if (textKey.includes('update')) return 'update';
    if (textKey.includes('logout')) return 'logout';
    if (textKey.includes('menu')) return 'menu';
    if (textKey.includes('view')) return 'view';
    if (textKey.includes('create') || textKey.includes('new')) return 'create';
    if (textKey.includes('search')) return 'search';
    if (textKey.includes('confirm')) return 'confirm';
    if (textKey.includes('cancel')) return 'cancel';
    return 'action';
  }

  function enhanceActionButtons(root) {
    const candidates = [];
    if (root instanceof HTMLButtonElement) {
      candidates.push(root);
    }
    if (root.querySelectorAll) {
      root.querySelectorAll('button').forEach((btn) => candidates.push(btn));
    }

    candidates.forEach((button) => {
      if (!isIconizationCandidate(button)) return;

      const hasGlyph = Boolean(button.querySelector('.icon-btn__glyph'));
      if (button.dataset.iconized === 'true' && hasGlyph) return;

      const rawLabel = (button.textContent || button.dataset.iconLabel || '').replace(/\s+/g, ' ').trim();
      const actionType = resolveActionType(button);
      if (!actionType) return;

      const label = actionLabel(actionType, rawLabel);
      const icon = iconSvgs[actionType] || iconSvgs.action;
      if (!icon) return;

      if (rawLabel) {
        button.dataset.iconLabel = rawLabel;
      }
      Array.from(button.classList).forEach((className) => {
        if (className.startsWith('icon-btn--')) {
          button.classList.remove(className);
        }
      });

      button.dataset.iconized = 'true';
      button.classList.add('icon-btn', `icon-btn--${actionType}`);
      button.setAttribute('aria-label', label);
      button.setAttribute('title', label);
      button.innerHTML = `<span class="icon-btn__glyph" aria-hidden="true">${icon}</span><span class="sr-only">${label}</span>`;
    });
  }

  function findSelectLabelText(select) {
    if (!select || !select.id) return '';
    const label = Array.from(document.querySelectorAll('label')).find((node) => node.htmlFor === select.id);
    return label ? (label.textContent || '').trim() : '';
  }

  function getSelectPlaceholder(select) {
    const emptyOption = Array.from(select.options).find((option) => String(option.value) === '');
    if (emptyOption) return (emptyOption.textContent || '').trim() || 'Select...';
    return 'Select...';
  }

  function getSelectDisplayText(select) {
    const selected = select.options[select.selectedIndex];
    if (!selected) return getSelectPlaceholder(select);
    const text = (selected.textContent || '').trim();
    if (text) return text;
    return getSelectPlaceholder(select);
  }

  function setSelectTriggerText(state) {
    if (!state || !state.trigger || !state.select) return;
    state.trigger.textContent = getSelectDisplayText(state.select);
  }

  function closeSelectPanel(state) {
    if (!state || !state.root || state.root.dataset.open !== 'true') return;
    state.root.dataset.open = 'false';
    state.trigger.setAttribute('aria-expanded', 'false');
    state.panel.hidden = true;
    state.search.value = '';
    filterSelectItems(state, '');
  }

  function openSelectPanel(state) {
    if (!state || !state.root) return;
    enhancedSelectState.forEach((entry) => {
      if (entry !== state) {
        closeSelectPanel(entry);
      }
    });
    state.root.dataset.open = 'true';
    state.trigger.setAttribute('aria-expanded', 'true');
    state.panel.hidden = false;
    state.search.value = '';
    filterSelectItems(state, '');
    setTimeout(() => state.search.focus(), 0);
  }

  function filterSelectItems(state, query) {
    if (!state || !state.optionsWrap) return;
    const normalized = String(query || '').trim().toLowerCase();
    state.optionsWrap.querySelectorAll('.select2-lite__option').forEach((item) => {
      const text = (item.dataset.search || '').toLowerCase();
      item.hidden = normalized ? !text.includes(normalized) : false;
    });
  }

  function rebuildSelectItems(state) {
    if (!state || !state.select || !state.optionsWrap) return;
    const { select, optionsWrap } = state;
    optionsWrap.innerHTML = '';
    const currentValue = String(select.value ?? '');

    Array.from(select.options).forEach((option) => {
      const value = String(option.value ?? '');
      const label = (option.textContent || '').trim() || value || getSelectPlaceholder(select);
      const item = document.createElement('button');
      item.type = 'button';
      item.className = 'select2-lite__option';
      item.dataset.value = value;
      item.dataset.search = `${label} ${value}`.trim();
      item.textContent = label;
      item.setAttribute('role', 'option');
      item.setAttribute('aria-selected', value === currentValue ? 'true' : 'false');
      item.classList.toggle('is-selected', value === currentValue);
      item.disabled = option.disabled;
      item.addEventListener('click', () => {
        if (item.disabled) return;
        if (select.value !== value) {
          select.value = value;
          select.dispatchEvent(new Event('change', { bubbles: true }));
        } else {
          setSelectTriggerText(state);
        }
        closeSelectPanel(state);
      });
      optionsWrap.appendChild(item);
    });

    setSelectTriggerText(state);
    filterSelectItems(state, state.search.value);
  }

  function syncSelectState(state) {
    if (!state || !state.select) return;
    const currentValue = String(state.select.value ?? '');
    const disabled = Boolean(state.select.disabled);
    const hidden = Boolean(state.select.hidden);
    if (state.lastValue !== currentValue) {
      state.lastValue = currentValue;
      rebuildSelectItems(state);
    }
    if (state.lastDisabled !== disabled) {
      state.lastDisabled = disabled;
      state.trigger.disabled = disabled;
      if (disabled) closeSelectPanel(state);
    }
    if (state.lastHidden !== hidden) {
      state.lastHidden = hidden;
      state.root.hidden = hidden;
      if (hidden) closeSelectPanel(state);
    }
  }

  function startSearchableSelectSync() {
    if (searchableSelectSyncTimer) return;
    searchableSelectSyncTimer = window.setInterval(() => {
      enhancedSelectState.forEach((state, select) => {
        if (!document.contains(select)) {
          if (state.optionsObserver) {
            state.optionsObserver.disconnect();
          }
          enhancedSelectState.delete(select);
          return;
        }
        syncSelectState(state);
      });
      if (enhancedSelectState.size === 0 && searchableSelectSyncTimer) {
        window.clearInterval(searchableSelectSyncTimer);
        searchableSelectSyncTimer = null;
      }
    }, 160);
  }

  function enhanceSearchableSelect(select) {
    if (!(select instanceof HTMLSelectElement)) return;
    if (select.dataset.searchableEnhanced === 'true') return;
    if (select.multiple || select.size > 1) return;
    if (select.dataset.noSearch === 'true') return;

    const parent = select.parentElement;
    if (!parent) return;

    const root = document.createElement('div');
    root.className = 'select2-lite';
    root.dataset.open = 'false';

    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className = 'select2-lite__trigger';
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');
    trigger.disabled = select.disabled;

    const panel = document.createElement('div');
    panel.className = 'select2-lite__panel';
    panel.hidden = true;

    const search = document.createElement('input');
    search.type = 'search';
    search.className = 'select2-lite__search';
    search.autocomplete = 'off';
    search.spellcheck = false;
    search.placeholder = 'Search...';
    const labelText = findSelectLabelText(select);
    search.setAttribute('aria-label', labelText ? `Search ${labelText}` : 'Search options');

    const optionsWrap = document.createElement('div');
    optionsWrap.className = 'select2-lite__options';
    optionsWrap.setAttribute('role', 'listbox');

    panel.appendChild(search);
    panel.appendChild(optionsWrap);

    parent.insertBefore(root, select);
    root.appendChild(trigger);
    root.appendChild(panel);
    root.appendChild(select);

    select.classList.add('select2-lite__native');
    select.dataset.searchableEnhanced = 'true';

    const state = {
      select,
      root,
      trigger,
      panel,
      search,
      optionsWrap,
      lastValue: String(select.value ?? ''),
      lastDisabled: Boolean(select.disabled),
      lastHidden: Boolean(select.hidden),
      optionsObserver: null
    };

    trigger.addEventListener('click', (event) => {
      event.preventDefault();
      if (trigger.disabled) return;
      if (root.dataset.open === 'true') {
        closeSelectPanel(state);
      } else {
        openSelectPanel(state);
      }
    });
    search.addEventListener('input', () => filterSelectItems(state, search.value));
    search.addEventListener('keydown', (event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeSelectPanel(state);
        trigger.focus();
        return;
      }
      if (event.key === 'ArrowDown') {
        const first = optionsWrap.querySelector('.select2-lite__option:not([hidden]):not(:disabled)');
        if (first) {
          event.preventDefault();
          first.focus();
        }
      }
    });
    optionsWrap.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      closeSelectPanel(state);
      trigger.focus();
    });
    select.addEventListener('change', () => {
      state.lastValue = String(select.value ?? '');
      rebuildSelectItems(state);
    });

    state.optionsObserver = new MutationObserver(() => rebuildSelectItems(state));
    state.optionsObserver.observe(select, { childList: true, subtree: false });
    enhancedSelectState.set(select, state);
    startSearchableSelectSync();
    rebuildSelectItems(state);
  }

  function enhanceSearchableSelects(root) {
    if (root instanceof HTMLSelectElement) {
      enhanceSearchableSelect(root);
    }
    if (root.querySelectorAll) {
      root.querySelectorAll('select').forEach((select) => enhanceSearchableSelect(select));
    }
  }

  function bindSelectOutsideClickHandler() {
    if (selectOutsideClickBound) return;
    selectOutsideClickBound = true;
    document.addEventListener('click', (event) => {
      enhancedSelectState.forEach((state) => {
        if (!state.root.contains(event.target)) {
          closeSelectPanel(state);
        }
      });
    });
  }

  function normalizeRequestPath(url) {
    try {
      return new URL(String(url || ''), window.location.origin).pathname;
    } catch {
      return String(url || '');
    }
  }

  function shouldRetryWithRefresh(url, status) {
    if (status !== 401 && status !== 403) return false;
    const path = normalizeRequestPath(url);
    return !path.startsWith('/auth/login')
      && !path.startsWith('/auth/refresh')
      && !path.startsWith('/auth/logout')
      && !path.startsWith('/auth/csrf');
  }

  async function refreshSession() {
    if (sessionRefreshInFlight) {
      return sessionRefreshInFlight;
    }
    sessionRefreshInFlight = (async () => {
      try {
        const response = await fetch('/auth/refresh', {
          method: 'POST',
          credentials: 'same-origin',
          headers: { Accept: 'application/json' }
        });
        return response.ok;
      } catch {
        return false;
      } finally {
        sessionRefreshInFlight = null;
      }
    })();
    return sessionRefreshInFlight;
  }

  async function fetchWithRefresh(url, options = {}) {
    const requestOptions = {
      credentials: 'same-origin',
      ...options
    };
    let response = await fetch(url, requestOptions);
    if (shouldRetryWithRefresh(url, response.status)) {
      const refreshed = await refreshSession();
      if (refreshed) {
        response = await fetch(url, requestOptions);
      }
    }
    return response;
  }

  function startSessionKeepAlive() {
    if (sessionKeepAliveTimer != null) return;

    const tick = () => {
      if (document.visibilityState !== 'visible') return;
      refreshSession().catch(() => {});
    };

    sessionKeepAliveTimer = window.setInterval(tick, SESSION_REFRESH_INTERVAL_MS);

    if (!sessionKeepAliveVisibilityBound) {
      sessionKeepAliveVisibilityBound = true;
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
          refreshSession().catch(() => {});
        }
      });
    }
  }

  function stopSessionKeepAlive() {
    if (sessionKeepAliveTimer == null) return;
    window.clearInterval(sessionKeepAliveTimer);
    sessionKeepAliveTimer = null;
  }

  function readCookie(name) {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]*)`));
    return match ? decodeURIComponent(match[1]) : null;
  }

  function createConfirmState() {
    return {
      root: null,
      panel: null,
      title: null,
      message: null,
      cancelBtn: null,
      confirmBtn: null,
      resolver: null,
      lastFocus: null
    };
  }

  function initConfirmDialog() {
    if (confirmState.root) return;

    const root = document.createElement('div');
    root.className = 'confirm-backdrop';
    root.id = 'confirmBackdrop';
    root.hidden = true;
    root.setAttribute('aria-hidden', 'true');

    const panel = document.createElement('section');
    panel.className = 'confirm-panel';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-modal', 'true');
    panel.setAttribute('aria-labelledby', 'confirmTitle');
    panel.setAttribute('aria-describedby', 'confirmMessage');
    panel.tabIndex = -1;

    const title = document.createElement('h2');
    title.id = 'confirmTitle';
    title.className = 'confirm-title';
    title.textContent = 'Confirm action';

    const message = document.createElement('p');
    message.id = 'confirmMessage';
    message.className = 'confirm-message';
    message.textContent = 'Are you sure?';

    const actions = document.createElement('div');
    actions.className = 'confirm-actions';

    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'secondary';
    cancelBtn.textContent = 'Cancel';

    const confirmBtn = document.createElement('button');
    confirmBtn.type = 'button';
    confirmBtn.textContent = 'Confirm';

    actions.appendChild(cancelBtn);
    actions.appendChild(confirmBtn);
    panel.appendChild(title);
    panel.appendChild(message);
    panel.appendChild(actions);
    root.appendChild(panel);
    document.body.appendChild(root);

    root.addEventListener('click', (event) => {
      if (event.target !== root) return;
      closeConfirmDialog(false);
    });
    cancelBtn.addEventListener('click', () => closeConfirmDialog(false));
    confirmBtn.addEventListener('click', () => closeConfirmDialog(true));
    document.addEventListener('keydown', (event) => {
      if (root.hidden) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        closeConfirmDialog(false);
        return;
      }
      if (event.key !== 'Tab') return;
      const focusables = [cancelBtn, confirmBtn].filter((node) => !node.disabled && !node.hidden);
      if (!focusables.length) {
        event.preventDefault();
        return;
      }
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    });

    confirmState.root = root;
    confirmState.panel = panel;
    confirmState.title = title;
    confirmState.message = message;
    confirmState.cancelBtn = cancelBtn;
    confirmState.confirmBtn = confirmBtn;
  }

  function openConfirmDialog(options) {
    if (!isValidConfirmRequest(options)) {
      return Promise.resolve(false);
    }
    if (!confirmState.root) {
      initConfirmDialog();
    }
    if (!confirmState.root) {
      const fallbackMessage = typeof options === 'string' ? options : (options && options.message) || 'Are you sure?';
      return Promise.resolve(window.confirm(fallbackMessage));
    }

    if (confirmState.resolver) {
      confirmState.resolver(false);
      confirmState.resolver = null;
    }

    const config = typeof options === 'string' ? { message: options } : (options || {});
    confirmState.title.textContent = config.title || 'Confirm action';
    confirmState.message.textContent = config.message || 'Are you sure?';
    confirmState.cancelBtn.textContent = config.cancelLabel || 'Cancel';
    confirmState.confirmBtn.textContent = config.confirmLabel || 'Confirm';
    confirmState.confirmBtn.classList.toggle('danger', Boolean(config.danger));
    confirmState.lastFocus = document.activeElement;

    confirmState.root.hidden = false;
    confirmState.root.setAttribute('aria-hidden', 'false');
    document.body.setAttribute('data-confirm-open', 'true');
    updateBodyScrollLock();

    setTimeout(() => {
      confirmState.confirmBtn.focus();
    }, 0);

    return new Promise((resolve) => {
      confirmState.resolver = resolve;
    });
  }

  function isValidConfirmRequest(options) {
    if (typeof options === 'string') {
      return options.trim().length > 0;
    }
    if (!options || typeof options !== 'object') {
      return false;
    }
    if (typeof options.message === 'string' && options.message.trim().length > 0) {
      return true;
    }
    if (typeof options.title === 'string' && options.title.trim().length > 0) {
      return true;
    }
    return false;
  }

  function closeConfirmDialog(result) {
    if (!confirmState.root || confirmState.root.hidden) return;
    confirmState.root.hidden = true;
    confirmState.root.setAttribute('aria-hidden', 'true');
    document.body.removeAttribute('data-confirm-open');
    updateBodyScrollLock();

    if (confirmState.lastFocus && typeof confirmState.lastFocus.focus === 'function') {
      confirmState.lastFocus.focus();
    }
    const resolver = confirmState.resolver;
    confirmState.resolver = null;
    if (resolver) {
      resolver(Boolean(result));
    }
  }

  function updateBodyScrollLock() {
    const hasOpenNav = document.body.getAttribute('data-nav-open') === 'true';
    const hasOpenConfirm = document.body.getAttribute('data-confirm-open') === 'true';
    if (hasOpenNav || hasOpenConfirm) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.removeProperty('overflow');
    }
  }

})();
