-- Persist the latest reference-data sync run so UI status survives app restarts.

CREATE TABLE IF NOT EXISTS reference_data_sync_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    trigger TEXT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    lifecycle_upserts INTEGER NOT NULL DEFAULT 0,
    app_catalog_upserts INTEGER NOT NULL DEFAULT 0,
    ios_enriched_rows INTEGER NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    errors_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

