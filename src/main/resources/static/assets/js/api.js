// Get API version from meta tag (set by Spring Boot property)
const API_VERSION = document.querySelector('meta[name="api-version"]')?.content || 'v1';
const SAFE_HTTP_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);
let refreshInFlight = null;

// Helper to build versioned API URLs
export function apiPath(path) {
  if (!path) return path;
  // Remove any existing version prefix (e.g., /v1/, /v2/) and replace with current version
  const unversionedPath = path.replace(/^\/v\d+\//, '/');
  return `/${API_VERSION}${unversionedPath}`;
}

// Internal fetch that auto-versions API URLs
export async function apiFetch(url, options = {}) {
  // Auto-version API URLs that start with known API prefixes
  if (url && typeof url === 'string') {
    url = apiPath(url);
  }
  const method = (options.method || 'GET').toUpperCase();
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };

  if (!SAFE_HTTP_METHODS.has(method)) {
    const csrfToken = await ensureCsrfToken(false);
    if (csrfToken) {
      headers['X-XSRF-TOKEN'] = csrfToken;
    }
  }

  let res = await fetch(url, {
    ...options,
    credentials: 'same-origin',
    headers
  });

  // Retry once after forcing a fresh CSRF token if write call is rejected.
  if (res.status === 403 && !SAFE_HTTP_METHODS.has(method)) {
    const refreshedToken = await ensureCsrfToken(true);
    if (refreshedToken) {
      const retryHeaders = {
        ...headers,
        'X-XSRF-TOKEN': refreshedToken
      };
      res = await fetch(url, {
        ...options,
        credentials: 'same-origin',
        headers: retryHeaders
      });
    }
  }

  // If session is stale (expired access token), refresh and retry once.
  if (shouldRetryWithRefresh(url, res.status)) {
    const refreshed = await refreshSession();
    if (refreshed) {
      const retryHeaders = { ...headers };
      if (!SAFE_HTTP_METHODS.has(method)) {
        const token = await ensureCsrfToken(false);
        if (token) retryHeaders['X-XSRF-TOKEN'] = token;
      }
      res = await fetch(url, {
        ...options,
        credentials: 'same-origin',
        headers: retryHeaders
      });
    }
  }

  if (res.status === 204) return null;

  const text = await res.text();
  const body = text ? safeJson(text) : null;

  if (!res.ok) {
    const msg = body?.message || body?.error || `${res.status} ${res.statusText}`;
    throw new Error(msg);
  }
  return body;
}

function normalizePagedRows(payload) {
  if (Array.isArray(payload)) {
    return payload;
  }
  if (Array.isArray(payload?.content)) {
    return payload.content;
  }
  if (Array.isArray(payload?.data)) {
    return payload.data;
  }
  if (Array.isArray(payload?.items)) {
    return payload.items;
  }
  return [];
}

function withPageParams(url, page, size) {
  const target = new URL(String(url || ''), window.location.origin);
  target.searchParams.set('page', String(page));
  target.searchParams.set('size', String(size));
  return `${target.pathname}${target.search}`;
}

export async function apiFetchAllPages(url, options = {}) {
  const {
    pageSize = 100,
    maxPages = 100,
    normalizeRows = normalizePagedRows,
    ...fetchOptions
  } = options;

  const rows = [];
  for (let page = 0; page < maxPages; page += 1) {
    const payload = await apiFetch(withPageParams(url, page, pageSize), fetchOptions);
    const pageRows = normalizeRows(payload);
    if (!Array.isArray(pageRows) || pageRows.length === 0) {
      break;
    }
    rows.push(...pageRows);
    if (pageRows.length < pageSize) {
      break;
    }
  }
  return rows;
}

export function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

export async function fetchAuthenticatedUser() {
  let response = await fetch('/auth/me', {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' }
  });
  if (response.ok) {
    const me = await response.json().catch(() => null);
    if (me && me.authenticated) {
      return me;
    }
  }

  const refreshed = await refreshSession();
  if (!refreshed) {
    return null;
  }

  response = await fetch('/auth/me', {
    credentials: 'same-origin',
    headers: { Accept: 'application/json' }
  });
  if (!response.ok) {
    return null;
  }
  const me = await response.json().catch(() => null);
  return me && me.authenticated ? me : null;
}

function readCookie(name) {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

async function ensureCsrfToken(forceRefresh) {
  if (forceRefresh) {
    eraseCookie('XSRF-TOKEN');
  }
  const existing = readCookie('XSRF-TOKEN');
  if (existing) return existing;
  try {
    const res = await fetch('/auth/csrf', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    });
    if (res.ok) {
      const body = await res.json().catch(() => ({}));
      const token = body?.token || '';
      if (token && !readCookie('XSRF-TOKEN')) {
        // Fallback for environments where Set-Cookie from csrf endpoint is not persisted.
        document.cookie = `XSRF-TOKEN=${encodeURIComponent(token)}; path=/; samesite=lax`;
      }
      if (token) {
        return token;
      }
    }
  } catch {
    return null;
  }
  return readCookie('XSRF-TOKEN');
}

function eraseCookie(name) {
  document.cookie = `${name}=; path=/; max-age=0`;
}

function shouldRetryWithRefresh(url, status) {
  if (status !== 401 && status !== 403) {
    return false;
  }
  const normalizedPath = normalizePath(url);
  return !normalizedPath.startsWith('/auth/login')
    && !normalizedPath.startsWith('/auth/refresh')
    && !normalizedPath.startsWith('/auth/logout');
}

function normalizePath(url) {
  try {
    return new URL(String(url || ''), window.location.origin).pathname;
  } catch {
    return String(url || '');
  }
}

export async function refreshSession() {
  if (refreshInFlight) {
    return refreshInFlight;
  }
  refreshInFlight = (async () => {
    try {
      const res = await fetch('/auth/refresh', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { Accept: 'application/json' }
      });
      return res.ok;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}
