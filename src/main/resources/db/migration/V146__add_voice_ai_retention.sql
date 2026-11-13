ALTER TABLE voice_ai_replay ADD COLUMN completed_at TIMESTAMP(6) NULL;
ALTER TABLE voice_ai_replay ADD COLUMN processing_lease_until TIMESTAMP(6) NULL;

-- 과거 결과는 JVM 로컬 시각으로 제한 배치에서 한 번 초기화합니다.
-- DB timezone 추정과 전체 결과의 일괄 UPDATE를 피합니다.

CREATE INDEX idx_voice_ai_session_closed_retention ON voice_ai_session(closed_at, session_id)
WHERE status = 'CLOSED';
CREATE INDEX idx_voice_ai_session_expired_retention ON voice_ai_session(expires_at, session_id)
WHERE status = 'ACTIVE';
CREATE INDEX idx_voice_ai_replay_completed_retention ON voice_ai_replay(completed_at, session_id, voice_ai_replay_id)
WHERE status = 'COMPLETED';
