INSERT INTO auth_user (username, password_hash, role, tenant_id, status, created_at, created_by, modified_at, modified_by, is_deleted)
VALUES (
  'admin',
  '$2a$10$7EqJtq98hPqEX7fNZaFWoO5lgOG/eo8pLG3dqQ.C1D7tY8F/5F86.',
  'PRODUCT_ADMIN',
  NULL,
  'ACTIVE',
  now(),
  'system',
  now(),
  'system',
  FALSE
)
ON CONFLICT (username) DO NOTHING;

