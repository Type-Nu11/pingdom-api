CREATE TABLE trust_score_change_history (
    id BIGSERIAL PRIMARY KEY,
    reporter_user_id BIGINT NOT NULL,
    before_score INTEGER NOT NULL CHECK (before_score BETWEEN 0 AND 100),
    after_score INTEGER NOT NULL CHECK (after_score BETWEEN 0 AND 100),
    reason VARCHAR(30) NOT NULL,
    changed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_trust_score_change_history_reporter_changed
    ON trust_score_change_history (reporter_user_id, changed_at DESC, id DESC);
