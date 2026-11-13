-- 업무 이력의 보존 정책은 바꾸지 않고 실패한 선두 사용자를 다음 조회에서 일시 제외한다.
CREATE TABLE user_purge_retry (
    user_id BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    reason_code VARCHAR(32) NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT ck_user_purge_retry_reason CHECK (reason_code IN ('RETAINED_REFERENCE', 'PROCESSING_FAILED')),
    CONSTRAINT ck_user_purge_retry_schedule CHECK (next_attempt_at > updated_at)
);

CREATE INDEX idx_user_purge_retry_next_attempt ON user_purge_retry (next_attempt_at, user_id);
