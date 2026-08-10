ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_event_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'SUCCEEDED', 'FAILED')),
    ADD CONSTRAINT ck_outbox_event_attempt_count
        CHECK (attempt_count >= 0);

CREATE INDEX IF NOT EXISTS idx_outbox_event_status_created
    ON outbox_event (status, created_at DESC, event_id DESC);

CREATE INDEX IF NOT EXISTS idx_outbox_event_type_created
    ON outbox_event (event_type, created_at DESC, event_id DESC);

CREATE INDEX IF NOT EXISTS idx_outbox_event_aggregate_created
    ON outbox_event (aggregate_type, aggregate_id, created_at DESC, event_id DESC);
