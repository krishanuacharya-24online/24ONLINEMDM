ALTER TABLE device_trust_score_event
  ADD COLUMN IF NOT EXISTS posture_evaluation_run_id BIGINT
  REFERENCES posture_evaluation_run(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_trust_event_eval_run
  ON device_trust_score_event (posture_evaluation_run_id, event_time DESC);
