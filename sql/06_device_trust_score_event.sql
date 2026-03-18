CREATE TABLE IF NOT EXISTS device_trust_score_event (
  id BIGSERIAL PRIMARY KEY,

  device_trust_profile_id BIGINT NOT NULL REFERENCES device_trust_profile(id) ON DELETE CASCADE,
  event_source TEXT NOT NULL CHECK (event_source IN ('SYSTEM_RULE', 'REJECT_APPLICATION', 'POSTURE_SIGNAL', 'MANUAL')),
  source_record_id BIGINT,

  trust_score_policy_id BIGINT REFERENCES trust_score_policy(id),
  system_information_rule_id BIGINT REFERENCES system_information_rule(id),
  reject_application_list_id BIGINT REFERENCES reject_application_list(id),
  os_release_lifecycle_master_id BIGINT,
  os_lifecycle_state TEXT,

  observed_payload JSONB,
  score_before SMALLINT NOT NULL CHECK (score_before BETWEEN 0 AND 100),
  score_delta SMALLINT NOT NULL CHECK (score_delta BETWEEN -1000 AND 1000),
  score_after SMALLINT NOT NULL CHECK (score_after BETWEEN 0 AND 100),

  event_time TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_by TEXT NOT NULL,
  notes TEXT,

  CONSTRAINT ck_trust_event_score_math
    CHECK (score_after = LEAST(100, GREATEST(0, score_before + score_delta))),
  CONSTRAINT ck_trust_event_os_lifecycle_state
    CHECK (
      os_lifecycle_state IS NULL
      OR os_lifecycle_state IN ('SUPPORTED', 'EOL', 'EEOL', 'NOT_TRACKED')
    )
);

CREATE INDEX IF NOT EXISTS idx_trust_event_profile_time
  ON device_trust_score_event (device_trust_profile_id, event_time DESC);

CREATE INDEX IF NOT EXISTS idx_trust_event_source
  ON device_trust_score_event (event_source, event_time DESC);

CREATE INDEX IF NOT EXISTS idx_trust_event_os_lifecycle
  ON device_trust_score_event (os_release_lifecycle_master_id, event_time DESC);
