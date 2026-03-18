CREATE TABLE IF NOT EXISTS reject_application_list (
  id BIGSERIAL PRIMARY KEY,

  policy_tag TEXT NOT NULL,
  threat_type TEXT NOT NULL,
  severity SMALLINT NOT NULL CHECK (severity BETWEEN 1 AND 5),
  blocked_reason TEXT NOT NULL,

  app_name TEXT NOT NULL,
  publisher TEXT,
  package_id TEXT,
  app_category TEXT NOT NULL,
  app_os_type TEXT NOT NULL CHECK (app_os_type IN ('ANDROID', 'IOS', 'WINDOWS', 'MACOS', 'LINUX', 'CHROMEOS', 'FREEBSD', 'OPENBSD')),

  app_latest_version TEXT NOT NULL,
  min_allowed_version TEXT NOT NULL,
  latest_ver_major INT,
  latest_ver_minor INT,
  latest_ver_patch INT,
  min_ver_major INT,
  min_ver_minor INT,
  min_ver_patch INT,

  status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
  effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  effective_to TIMESTAMPTZ,
  is_deleted BOOLEAN NOT NULL DEFAULT false,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  modified_by TEXT NOT NULL,

  CONSTRAINT ck_reject_effective_window
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reject_app_expr
  ON reject_application_list (app_os_type, COALESCE(package_id, ''), lower(app_name), policy_tag)
  WHERE is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_reject_status
  ON reject_application_list (status, is_deleted);

CREATE INDEX IF NOT EXISTS idx_reject_os_category
  ON reject_application_list (app_os_type, app_category);

CREATE INDEX IF NOT EXISTS idx_reject_policy
  ON reject_application_list (policy_tag, threat_type, severity);

CREATE INDEX IF NOT EXISTS idx_reject_match_active
  ON reject_application_list (app_os_type, COALESCE(package_id, ''), lower(app_name))
  WHERE status = 'ACTIVE' AND is_deleted = false;

CREATE INDEX IF NOT EXISTS idx_reject_appname_trgm
  ON reject_application_list USING gin (app_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_reject_publisher_trgm
  ON reject_application_list USING gin (publisher gin_trgm_ops);
