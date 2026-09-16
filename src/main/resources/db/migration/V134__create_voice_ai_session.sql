CREATE TABLE voice_ai_session (
    session_id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP NULL
);

CREATE INDEX idx_voice_ai_session_user_id ON voice_ai_session(user_id);
