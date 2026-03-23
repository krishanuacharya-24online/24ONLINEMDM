import { apiFetch, apiFetchAllPages, fetchAuthenticatedUser } from '../api.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';

const ROLE_OPTIONS = ['PRODUCT_ADMIN', 'TENANT_ADMIN', 'TENANT_USER'];
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];
const session = {
  role: null,
  userId: null,
  tenantId: null
};

function esc(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

async function confirmAction({ message, title = 'Confirm action', confirmLabel = 'Confirm', cancelLabel = 'Cancel', danger = false }) {
  if (typeof window.mdmConfirm === 'function') {
    return window.mdmConfirm({
      title,
      message,
      confirmLabel,
      cancelLabel,
      danger
    });
  }
  return window.confirm(message);
}

async function confirmDanger(message, title = 'Confirm deletion') {
  return confirmAction({
    title,
    message,
    confirmLabel: 'Delete',
    cancelLabel: 'Cancel',
    danger: true
  });
}

async function confirmInvalidateTokens(username) {
  const subject = username ? ` for ${username}` : '';
  return confirmAction({
    title: 'Invalidate all tokens',
    message: `Invalidate all JWT tokens${subject}? The user will need to log in again on all devices.`,
    confirmLabel: 'Invalidate',
    cancelLabel: 'Cancel',
    danger: true
  });
}

async function confirmInvalidateAllTokens() {
  return confirmAction({
    title: 'Invalidate all user tokens',
    message: 'Invalidate all JWT tokens for every user except the protected admin account? All affected users will need to log in again.',
    confirmLabel: 'Invalidate all',
    cancelLabel: 'Cancel',
    danger: true
  });
}

function el(id) {
  return document.getElementById(id);
}

function isStrongPassword(password) {
  if (!password || password.length < 12) return false;
  let hasUpper = false;
  let hasLower = false;
  let hasDigit = false;
  let hasSpecial = false;
  for (const ch of password) {
    if (/[A-Z]/.test(ch)) hasUpper = true;
    else if (/[a-z]/.test(ch)) hasLower = true;
    else if (/[0-9]/.test(ch)) hasDigit = true;
    else hasSpecial = true;
  }
  return hasUpper && hasLower && hasDigit && hasSpecial;
}

function getValue(obj, ...keys) {
  for (const k of keys) {
    if (obj && obj[k] !== undefined && obj[k] !== null) {
      return obj[k];
    }
  }
  return null;
}

async function loadCurrentUser() {
  const me = await fetchAuthenticatedUser().catch(() => null);
  if (!me || !me.authenticated) return;
  session.role = String(me.role || '').trim().toUpperCase() || null;
  session.userId = me.userId == null ? null : Number(me.userId);
  session.tenantId = me.tenantId == null ? null : Number(me.tenantId);
}

async function initFormDropdowns() {
  const roleOptions = session.role === 'TENANT_ADMIN' ? ['TENANT_USER'] : ROLE_OPTIONS;
  await Promise.all([
    populateLookupSelect('role', {
      fallbackOptions: roleOptions
    }),
    populateLookupSelect('status', {
      lookupType: LOOKUP_TYPES.recordStatus,
      fallbackOptions: STATUS_OPTIONS
    })
  ]);
}

async function loadTenants() {
  const tenantSelect = el('tenant_id');
  if (!tenantSelect) return;
  if (session.role === 'TENANT_ADMIN') {
    tenantSelect.innerHTML = '<option value="">Scoped to your tenant</option>';
    tenantSelect.disabled = true;
    return;
  }
  const current = tenantSelect.value;
  const tenants = await apiFetchAllPages('/v1/admin/tenants', { pageSize: 100 });

  tenantSelect.innerHTML = '<option value="">None</option>';
  tenants.forEach((tenant) => {
    const code = getValue(tenant, 'tenant_id', 'tenantId');
    if (!code) return;
    const name = getValue(tenant, 'name') || '';
    const opt = document.createElement('option');
    opt.value = String(code);
    opt.textContent = name ? `${code} - ${name}` : String(code);
    tenantSelect.appendChild(opt);
  });

  if (current && tenantSelect.querySelector(`option[value="${current}"]`)) {
    tenantSelect.value = current;
  }
}

function applyRoleFormBehavior() {
  const role = el('role')?.value || '';
  const tenant = el('tenant_id');
  if (!tenant) return;
  if (session.role === 'TENANT_ADMIN') {
    tenant.disabled = true;
    tenant.value = '';
    return;
  }
  const isTenantUser = role === 'TENANT_USER';
  const isTenantAdmin = role === 'TENANT_ADMIN';
  tenant.disabled = !isTenantUser;
  if (!isTenantUser && !isTenantAdmin) {
    tenant.value = '';
  }
}

function fillForm(row) {
  const isEdit = Boolean(row && row.id != null);
  const passwordInput = el('password');
  el('edit_id').value = isEdit ? String(row.id) : '';
  el('username').value = getValue(row, 'username') || '';
  el('username').disabled = isEdit || session.role === 'TENANT_ADMIN';
  passwordInput.value = '';
  passwordInput.required = !isEdit;
  passwordInput.placeholder = isEdit ? 'Leave blank to keep current password' : 'Required';
  el('role').value = getValue(row, 'role') || 'PRODUCT_ADMIN';
  el('status').value = getValue(row, 'status') || 'ACTIVE';
  const tenantCode = getValue(row, 'tenant_id', 'tenantId') || '';
  el('tenant_id').value = tenantCode;
  applyRoleFormBehavior();
}

function renderUserEnrollments(rows, userId) {
  const tbody = el('userEnrollmentsTableBody');
  const hint = el('userEnrollmentsHint');
  if (!tbody || !hint) return;
  if (!userId) {
    hint.textContent = 'Select a user row to view enrolled devices.';
    tbody.innerHTML = '<tr><td colspan="9" class="muted">No user selected.</td></tr>';
    return;
  }
  hint.textContent = `Enrolled devices for user #${userId}`;
  if (!Array.isArray(rows) || !rows.length) {
    tbody.innerHTML = '<tr><td colspan="9" class="muted">No enrolled devices found.</td></tr>';
    return;
  }
  tbody.innerHTML = rows.map((row) => {
    const status = String(getValue(row, 'status') || '').toUpperCase();
    const canDeEnroll = status === 'ACTIVE';
    const rowId = getValue(row, 'id');
    const action = canDeEnroll
      ? `<button class="danger" type="button" data-act="de-enroll" data-id="${esc(rowId)}" data-user-id="${esc(userId)}">De-enroll</button>`
      : '<span class="muted">No actions</span>';
    return `
      <tr>
        <td>${esc(rowId ?? '---')}</td>
        <td>${esc(getValue(row, 'enrollment_no', 'enrollmentNo') ?? '---')}</td>
        <td>${esc(getValue(row, 'enrollment_method', 'enrollmentMethod') ?? '---')}</td>
        <td>${esc(status || '---')}</td>
        <td>${esc(getValue(row, 'agent_id', 'agentId') ?? '---')}</td>
        <td>${esc(getValue(row, 'enrolled_at', 'enrolledAt') ?? '---')}</td>
        <td>${esc(getValue(row, 'de_enrolled_at', 'deEnrolledAt') ?? '---')}</td>
        <td>${esc(getValue(row, 'de_enroll_reason', 'deEnrollReason') ?? '---')}</td>
        <td>${action}</td>
      </tr>
    `;
  }).join('');
}

async function loadUserEnrollments(userId) {
  if (!userId) {
    renderUserEnrollments([], null);
    return;
  }
  const rows = await apiFetchAllPages(`/v1/devices/enrollments?user_id=${encodeURIComponent(userId)}`, { pageSize: 100 });
  renderUserEnrollments(rows, userId);
}

document.addEventListener('DOMContentLoaded', async () => {
  await loadCurrentUser().catch(() => {});
  await initFormDropdowns();
  await loadTenants();

  if (session.role === 'PRODUCT_ADMIN') {
    const card = el('userEnrollmentsCard');
    if (card) card.hidden = true;
    const invalidateAllBtn = el('invalidateAllTokensBtn');
    if (invalidateAllBtn) invalidateAllBtn.hidden = false;
  }
  if (session.role === 'TENANT_ADMIN') {
    const tenantWrap = el('tenantIdFieldWrap');
    const tenantLabelWrap = el('tenantIdLabelWrap');
    if (tenantWrap) tenantWrap.hidden = true;
    if (tenantLabelWrap) tenantLabelWrap.hidden = true;
  }

  const dt = window.mdmInitDataTable('#usersTable', {
    ajax: { url: '/v1/ui/datatables/users', dataSrc: 'data' },
    defaultSortBy: 'id',
    defaultSortDir: 'desc',
    columns: [
      { data: 'id' },
      { data: 'username' },
      { data: 'role' },
      { data: 'tenant_id' },
      { data: 'status' },
      { data: 'modified_at' },
      {
        data: null,
        orderable: false,
        render: (_, __, row) => {
          const username = String(getValue(row, 'username') || '').trim().toLowerCase();
          const actions = ['<button class="secondary" data-act="edit">Edit</button>'];
          if (session.role === 'PRODUCT_ADMIN' && username !== 'admin') {
            actions.push('<button class="secondary" data-act="invalidate">Invalidate tokens</button>');
          }
          actions.push('<button class="danger" data-act="del">Delete</button>');
          return actions.join(' ');
        }
      }
    ]
  });

  el('role')?.addEventListener('change', applyRoleFormBehavior);

  el('usersTable')?.addEventListener('click', async (e) => {
    const btn = e.target.closest('button[data-act]');
    if (!btn) return;
    const act = btn.getAttribute('data-act');

    const tr = btn.closest('tr');
    const row = tr ? dt.row(tr).data() : null;
    const id = row?.id;
    if (!id) return;

    if (act === 'edit') {
      fillForm(row);
      window.mdmToast?.(`Editing user #${id}`);
      if (session.role === 'TENANT_ADMIN') {
        loadUserEnrollments(id).catch((err) => window.mdmToast?.(`Error: ${err.message}`));
      }
      return;
    }
    if (act === 'invalidate') {
      const confirmed = await confirmInvalidateTokens(getValue(row, 'username'));
      if (!confirmed) return;
      await apiFetch(`/v1/admin/users/${encodeURIComponent(id)}/invalidate-tokens`, { method: 'POST' });
      window.mdmToast?.('All user tokens invalidated');
      dt.ajax.reload(null, false);
      return;
    }
    if (act === 'del') {
      const confirmed = await confirmDanger(`Delete user #${id}?`);
      if (!confirmed) return;
      await apiFetch(`/v1/admin/users/${encodeURIComponent(id)}`, { method: 'DELETE' });
      window.mdmToast?.('User deleted');
      dt.ajax.reload(null, false);
      fillForm(null);
      if (session.role === 'TENANT_ADMIN') {
        renderUserEnrollments([], null);
      }
    }
  });

  el('invalidateAllTokensBtn')?.addEventListener('click', async () => {
    if (session.role !== 'PRODUCT_ADMIN') return;
    const confirmed = await confirmInvalidateAllTokens();
    if (!confirmed) return;
    const response = await apiFetch('/v1/admin/users/invalidate-all-tokens', { method: 'POST' });
    const invalidated = Number(getValue(response, 'invalidatedUserCount', 'invalidated_user_count') || 0);
    const skipped = Number(getValue(response, 'skippedProtectedUserCount', 'skipped_protected_user_count') || 0);
    const revoked = Number(getValue(response, 'revokedRefreshTokenCount', 'revoked_refresh_token_count') || 0);
    window.mdmToast?.(`Invalidated ${invalidated} users, skipped ${skipped} protected account, revoked ${revoked} refresh tokens`);
    dt.ajax.reload(null, false);
    fillForm(null);
    renderUserEnrollments([], null);
  });

  el('userEnrollmentsTableBody')?.addEventListener('click', async (event) => {
    const button = event.target.closest('button[data-act="de-enroll"]');
    if (!button) return;
    const enrollmentId = button.getAttribute('data-id');
    const userId = button.getAttribute('data-user-id');
    if (!enrollmentId || !userId) return;

    const confirmed = await confirmDanger(`De-enroll enrollment #${enrollmentId}?`, 'Confirm de-enrollment');
    if (!confirmed) return;
    const reason = window.prompt('Optional reason for de-enrollment:', '') || '';
    await apiFetch(`/v1/devices/enrollments/${encodeURIComponent(enrollmentId)}/de-enroll`, {
      method: 'POST',
      body: JSON.stringify({ reason })
    });
    window.mdmToast?.('Device de-enrolled');
    await loadUserEnrollments(Number(userId));
  });

  el('usersForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = (el('edit_id').value || '').trim();
    const username = (el('username').value || '').trim();
    const role = el('role').value;
    const status = el('status').value;
    const tenantIdValue = (el('tenant_id').value || '').trim();
    const tenantId = session.role === 'TENANT_ADMIN'
      ? null
      : ((role === 'TENANT_USER' || role === 'TENANT_ADMIN') ? (tenantIdValue || null) : null);
    const passwordValue = el('password').value || '';
    const password = passwordValue ? passwordValue : null;

    if (!id) {
      if (!username) {
        window.mdmToast?.('Username is required');
        return;
      }
      if (!/^[a-zA-Z0-9._-]{3,64}$/.test(username)) {
        window.mdmToast?.('Username must be 3-64 chars using letters, numbers, ., _, -');
        return;
      }
      if (!password) {
        window.mdmToast?.('Password is required for new user');
        return;
      }
      if (!isStrongPassword(password)) {
        window.mdmToast?.('Password must be 12+ chars with upper, lower, number, special');
        return;
      }
    } else if (password && !isStrongPassword(password)) {
      window.mdmToast?.('Password must be 12+ chars with upper, lower, number, special');
      return;
    }

    if (session.role !== 'TENANT_ADMIN' && role === 'TENANT_USER' && !tenantId) {
      window.mdmToast?.('Tenant ID is required for TENANT_USER');
      return;
    }
    if (session.role === 'TENANT_ADMIN' && role !== 'TENANT_USER') {
      window.mdmToast?.('TENANT_ADMIN can only manage TENANT_USER');
      return;
    }

    if (!id) {
      await apiFetch('/v1/admin/users', {
        method: 'POST',
        body: JSON.stringify({
          username,
          password,
          role,
          status,
          tenantId
        })
      });
      window.mdmToast?.('User created');
    } else {
      await apiFetch(`/v1/admin/users/${encodeURIComponent(id)}`, {
        method: 'PUT',
        body: JSON.stringify({
          role,
          status,
          tenantId,
          password
        })
      });
      window.mdmToast?.('User updated');
    }

    dt.ajax.reload(null, false);
    fillForm(null);
    if (session.role !== 'TENANT_ADMIN') {
      await loadTenants();
    }
    if (session.role === 'TENANT_ADMIN') {
      renderUserEnrollments([], null);
    }
  });

  el('resetBtn')?.addEventListener('click', () => fillForm(null));
  fillForm(null);
  renderUserEnrollments([], null);
});
