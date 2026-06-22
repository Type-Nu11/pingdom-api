CREATE TABLE IF NOT EXISTS outbox_event (
    event_id VARCHAR(36) PRIMARY KEY,
    deduplication_key VARCHAR(200) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    processing_started_at TIMESTAMP(6),
    processed_at TIMESTAMP(6),
    last_error VARCHAR(2000),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uk_outbox_event_deduplication_key UNIQUE (deduplication_key)
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_ready
    ON outbox_event (status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_event_processing
    ON outbox_event (status, processing_started_at);
