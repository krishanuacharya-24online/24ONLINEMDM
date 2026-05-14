(() => {
  const MIN_VISIBLE_MS = 650;
  const NETWORK_IDLE_MS = 180;
  const MAX_VISIBLE_MS = 4000;
  const startedAt = Date.now();
  const nativeFetch = typeof window.fetch === 'function' ? window.fetch.bind(window) : null;

  let windowLoaded = document.readyState === 'complete';
  let pendingRequests = 0;
  let explicitHolds = 0;
  let hideStarted = false;
  let hideTimer = null;
  let idleTimer = null;

  function clearTimers() {
    if (hideTimer) {
      window.clearTimeout(hideTimer);
      hideTimer = null;
    }
    if (idleTimer) {
      window.clearTimeout(idleTimer);
      idleTimer = null;
    }
  }

  function hideLoader(force = false) {
    if (hideStarted) {
      return;
    }
    if (!force && (!windowLoaded || pendingRequests > 0 || explicitHolds > 0)) {
      return;
    }

    const loader = document.getElementById('pageLoader');
    const elapsed = Date.now() - startedAt;
    const delay = force ? 0 : Math.max(0, MIN_VISIBLE_MS - elapsed);

    hideStarted = true;
    clearTimers();

    hideTimer = window.setTimeout(() => {
      document.body.classList.remove('page-loading');
      if (loader) {
        loader.setAttribute('aria-hidden', 'true');
      }

      window.setTimeout(() => {
        if (loader) {
          loader.hidden = true;
        }
      }, 220);
    }, delay);
  }

  function scheduleHideCheck() {
    if (hideStarted) {
      return;
    }
    if (idleTimer) {
      window.clearTimeout(idleTimer);
    }
    idleTimer = window.setTimeout(() => hideLoader(false), NETWORK_IDLE_MS);
  }

  function markRequestStart() {
    pendingRequests += 1;
  }

  function markRequestEnd() {
    pendingRequests = Math.max(0, pendingRequests - 1);
    scheduleHideCheck();
  }

  function patchFetch() {
    if (!nativeFetch) {
      return;
    }

    window.fetch = (...args) => {
      if (hideStarted) {
        return nativeFetch(...args);
      }

      markRequestStart();
      try {
        return Promise.resolve(nativeFetch(...args)).finally(markRequestEnd);
      } catch (error) {
        markRequestEnd();
        throw error;
      }
    };
  }

  window.mdmPageLoader = {
    hold() {
      explicitHolds += 1;
      let released = false;
      return () => {
        if (released) {
          return;
        }
        released = true;
        explicitHolds = Math.max(0, explicitHolds - 1);
        scheduleHideCheck();
      };
    },
    ready() {
      explicitHolds = 0;
      scheduleHideCheck();
    },
    hide() {
      hideLoader(true);
    }
  };

  patchFetch();

  window.addEventListener('pageshow', (event) => {
    if (event.persisted) {
      hideLoader(true);
    }
  });

  window.setTimeout(() => hideLoader(true), MAX_VISIBLE_MS);

  if (windowLoaded) {
    scheduleHideCheck();
  } else {
    window.addEventListener('load', () => {
      windowLoaded = true;
      scheduleHideCheck();
    }, { once: true });
  }
})();
