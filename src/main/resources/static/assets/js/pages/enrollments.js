import { apiFetch, fetchAuthenticatedUser } from '../api.js';
import QRCode from '../vendor/qrcode.bundle.js';

const session = {
  role: null,
  userId: null,
  username: null
};

function esc(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function byId(id) {
  return document.getElementById(id);
}

function text(value) {
  if (value === null || value === undefined) return '---';
  const normalized = String(value).trim();
  return normalized || '---';
}

function toIsoLocal(value) {
  if (!value) return '---';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return text(value);
  return date.toLocaleString();
}

function tenantHeaders() {
  const tenant = (byId('enrollmentTenantId')?.value || '').trim().toLowerCase();
  if (!tenant) return {};
  return { 'X-Tenant-Id': tenant };
}

function toPositiveId(value) {
  const n = Number(value);
  if (!Number.isFinite(n) || n <= 0) return null;
  return Math.trunc(n);
}

function setTargetUserOptions(users) {
  const setupTarget = byId('setupKeyTargetUserId');
  if (!setupTarget) return;

  const normalizedUsers = Array.isArray(users)
    ? users
      .map((user) => ({
        id: toPositiveId(user?.id),
        username: text(user?.username || '')
      }))
      .filter((user) => user.id != null)
    : [];

  const previous = String(setupTarget.value || '').trim();
  setupTarget.innerHTML = '';

  const placeholder = document.createElement('option');
  placeholder.value = '';
  placeholder.textContent = normalizedUsers.length ? 'Select user' : 'No users available';
  setupTarget.appendChild(placeholder);

  normalizedUsers.forEach((user) => {
    const option = document.createElement('option');
    option.value = String(user.id);
    option.textContent = `${user.username} (#${user.id})`;
    setupTarget.appendChild(option);
  });

  if (previous && [...setupTarget.options].some((option) => option.value === previous)) {
    setupTarget.value = previous;
  }
}

function setTargetUserForAll(userId) {
  const id = toPositiveId(userId);
  if (!id) return;
  const setupTarget = byId('setupKeyTargetUserId');
  if (!setupTarget) return;
  const key = String(id);
  if ([...setupTarget.options].every((option) => option.value !== key)) {
    const option = document.createElement('option');
    option.value = key;
    const username = text(session.username || 'Current user');
    option.textContent = `${username} (#${key})`;
    setupTarget.appendChild(option);
  }
  setupTarget.value = key;
}

async function loadTargetUsers() {
  const setupTarget = byId('setupKeyTargetUserId');
  if (!setupTarget) return;

  const selfUserId = toPositiveId(session.userId);
  const selfUsername = text(session.username || (selfUserId ? `User ${selfUserId}` : 'Current user'));

  if (session.role === 'TENANT_USER') {
    const users = selfUserId ? [{ id: selfUserId, username: selfUsername }] : [];
    setTargetUserOptions(users);
    setTargetUserForAll(selfUserId);
    return;
  }

  if (session.role !== 'TENANT_ADMIN') {
    setTargetUserOptions([]);
    return;
  }

  const response = await apiFetch('/v1/ui/datatables/users?draw=1&start=0&length=500&role=TENANT_USER&status=ACTIVE&sort_by=username&sort_dir=asc');
  const rows = Array.isArray(response?.data) ? response.data : [];
  const users = rows.map((row) => ({
    id: toPositiveId(row?.id),
    username: text(row?.username)
  })).filter((user) => user.id != null);

  if (selfUserId && users.every((user) => user.id !== selfUserId)) {
    users.unshift({ id: selfUserId, username: selfUsername });
  }

  setTargetUserOptions(users);
}

function statusBadgeClass(status) {
  const s = String(status || '').toUpperCase();
  if (s === 'ACTIVE') return 'badge badge--trusted';
  if (s === 'DE_ENROLLED') return 'badge badge--danger';
  if (s === 'EXPIRED') return 'badge badge--warn';
  return 'badge';
}

function renderRows(rows) {
  const tbody = byId('enrollmentTableBody');
  if (!tbody) return;
  if (!rows.length) {
    tbody.innerHTML = '<tr><td colspan="10" class="muted">No enrollments found.</td></tr>';
    return;
  }

  tbody.innerHTML = rows.map((row) => {
    const status = text(row.status);
    const id = row.id;
    const action = status === 'ACTIVE'
      ? `<button class="danger" type="button" data-act="de-enroll" data-id="${esc(id)}">De-enroll</button>`
      : '<span class="muted">No actions</span>';
    return `
      <tr>
        <td>${esc(text(id))}</td>
        <td>${esc(text(row.enrollmentNo ?? row.enrollment_no))}</td>
        <td>${esc(text(row.ownerUserId ?? row.owner_user_id))}</td>
        <td>${esc(text(row.enrollmentMethod ?? row.enrollment_method))}</td>
        <td><span class="${statusBadgeClass(status)}">${esc(status)}</span></td>
        <td>${esc(text(row.agentId ?? row.agent_id))}</td>
        <td>${esc(toIsoLocal(row.enrolledAt ?? row.enrolled_at))}</td>
        <td>${esc(toIsoLocal(row.deEnrolledAt ?? row.de_enrolled_at))}</td>
        <td>${esc(text(row.deEnrollReason ?? row.de_enroll_reason))}</td>
        <td>${action}</td>
      </tr>
    `;
  }).join('');
}

async function loadEnrollments() {
  const status = (byId('enrollmentStatus')?.value || '').trim();
  const ownerUserId = (byId('enrollmentUserIdFilter')?.value || '').trim();
  const query = new URLSearchParams({ size: '200' });
  if (status) query.set('status', status);
  if (ownerUserId) query.set('user_id', ownerUserId);
  const data = await apiFetch(`/v1/devices/enrollments?${query.toString()}`, {
    headers: tenantHeaders()
  });
  renderRows(Array.isArray(data) ? data : []);
}

async function createSetupKey(event) {
  event.preventDefault();
  const maxUses = Number(byId('setupKeyMaxUses')?.value || 5);
  const ttlMinutes = Number(byId('setupKeyTtlMinutes')?.value || 60);
  const targetUserId = toPositiveId(byId('setupKeyTargetUserId')?.value || null);
  if (session.role === 'TENANT_ADMIN' && !targetUserId) {
    throw new Error('target_user_id is required for TENANT_ADMIN');
  }
  const result = byId('setupKeyResult');
  const qrWrap = byId('setupKeyQrWrap');
  const qrImage = byId('setupKeyQrImage');
  result.textContent = 'Generating enrollment key...';
  if (qrWrap) qrWrap.hidden = true;
  if (qrImage) {
    qrImage.removeAttribute('src');
    qrImage.hidden = true;
  }
  try {
    const payload = { max_uses: maxUses, ttl_minutes: ttlMinutes };
    if (targetUserId) payload.target_user_id = targetUserId;
    const body = await apiFetch('/v1/devices/enrollments/setup-keys', {
      method: 'POST',
      headers: tenantHeaders(),
      body: JSON.stringify(payload)
    });
    result.textContent = JSON.stringify(body, null, 2);
    const setupKey = String(body?.setupKey ?? body?.setup_key ?? '').trim();
    if (setupKey && qrWrap && qrImage) {
      try {
        const dataUrl = await QRCode.toDataURL(setupKey, {
          errorCorrectionLevel: 'M',
          margin: 1,
          width: 220
        });
        qrImage.src = dataUrl;
        qrImage.hidden = false;
        const label = qrWrap.querySelector('.muted');
        if (label) {
          label.textContent = 'Scan this QR to use the same enrollment key (generated locally in browser).';
        }
        qrWrap.hidden = false;
      } catch (qrError) {
        const label = qrWrap.querySelector('.muted');
        if (label) {
          label.textContent = 'QR preview unavailable. Use the setup key text shown above.';
        }
        qrWrap.hidden = false;
        result.textContent += '\n\nWarning: Unable to render QR locally.';
      }
    }
    window.mdmToast?.('Enrollment key generated');
  } catch (error) {
    result.textContent = `Error: ${error.message}`;
    throw error;
  }
}

async function deEnrollById(id) {
  const confirmed = typeof window.mdmConfirm === 'function'
    ? await window.mdmConfirm({
        title: 'Confirm de-enrollment',
        message: `De-enroll device enrollment #${id}?`,
        confirmLabel: 'De-enroll',
        cancelLabel: 'Cancel',
        danger: true
      })
    : window.confirm(`De-enroll device enrollment #${id}?`);
  if (!confirmed) return;

  const reason = window.prompt('Optional reason for de-enrollment:', '') || '';
  await apiFetch(`/v1/devices/enrollments/${encodeURIComponent(id)}/de-enroll`, {
    method: 'POST',
    headers: tenantHeaders(),
    body: JSON.stringify({ reason })
  });
  window.mdmToast?.('Device de-enrolled');
  await loadEnrollments();
}

async function loadCurrentUser() {
  const me = await fetchAuthenticatedUser().catch(() => null);
  if (!me || !me.authenticated) return;
  session.role = String(me.role || '').trim().toUpperCase() || null;
  const rawUserId = me.userId ?? me.uid ?? me.id ?? null;
  session.userId = rawUserId == null ? null : Number(rawUserId);
  session.username = String(me.username || '').trim() || null;
}

function applyRoleMode() {
  const isTenantUser = session.role === 'TENANT_USER';
  const selfUserId = toPositiveId(session.userId);

  const setupTarget = byId('setupKeyTargetUserId');
  const setupTargetWrap = byId('setupKeyTargetUserWrap');
  const userFilter = byId('enrollmentUserIdFilter');
  const tenantOverride = byId('enrollmentTenantId');

  if (isTenantUser) {
    setTargetUserForAll(selfUserId);
    if (setupTarget) setupTarget.disabled = true;
    if (setupTargetWrap) setupTargetWrap.hidden = true;
    if (userFilter) {
      userFilter.value = selfUserId ? String(selfUserId) : '';
      userFilter.disabled = true;
    }
    if (tenantOverride) {
      tenantOverride.value = '';
      tenantOverride.disabled = true;
    }
  } else {
    if (setupTarget) setupTarget.disabled = setupTarget.options.length <= 1;
    if (setupTargetWrap) setupTargetWrap.hidden = false;
    if (userFilter) userFilter.disabled = false;
    if (tenantOverride) tenantOverride.disabled = false;
  }
}

document.addEventListener('DOMContentLoaded', async () => {
  await loadCurrentUser().catch(() => {});
  await loadTargetUsers().catch((err) => window.mdmToast?.(`Error loading users: ${err.message}`));
  applyRoleMode();

  byId('setupKeyForm')?.addEventListener('submit', (e) => {
    createSetupKey(e).catch((err) => window.mdmToast?.(`Error: ${err.message}`));
  });
  byId('enrollmentScopeForm')?.addEventListener('submit', (e) => {
    e.preventDefault();
    loadEnrollments().catch((err) => window.mdmToast?.(`Error: ${err.message}`));
  });

  byId('enrollmentTableBody')?.addEventListener('click', (event) => {
    const btn = event.target.closest('button[data-act="de-enroll"]');
    if (!btn) return;
    const id = btn.getAttribute('data-id');
    if (!id) return;
    deEnrollById(id).catch((err) => window.mdmToast?.(`Error: ${err.message}`));
  });

  await loadEnrollments().catch((err) => {
    byId('enrollmentTableBody').innerHTML = `<tr><td colspan="10" class="muted">Error: ${esc(err.message)}</td></tr>`;
  });
});
