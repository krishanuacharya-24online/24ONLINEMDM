CREATE TABLE IF NOT EXISTS auth_user (
    id           BIGSERIAL PRIMARY KEY,
    username     TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role         TEXT NOT NULL, -- PRODUCT_ADMIN or TENANT_USER
    tenant_id    BIGINT NULL REFERENCES tenant_master(id),
    status       TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   TEXT NOT NULL DEFAULT 'system',
    modified_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_by  TEXT NOT NULL DEFAULT 'system',
    is_deleted   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS auth_refresh_token (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES auth_user(id),
    jti          TEXT NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   TEXT NOT NULL DEFAULT 'system'
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_user_id
    ON auth_refresh_token(user_id);

