ALTER TABLE voice_ai_replay
    ALTER COLUMN envelope DROP NOT NULL,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN processing_token VARCHAR(36) NULL,
    ADD COLUMN processing_started_at TIMESTAMP(6) NULL,
    ADD CONSTRAINT ck_voice_ai_replay_status CHECK (status IN ('PROCESSING', 'COMPLETED'));

CREATE INDEX idx_voice_ai_replay_processing
    ON voice_ai_replay (session_id, status, created_at DESC);
