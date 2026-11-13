package com.typenull.pingdom.consultation.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 세션 행 잠금을 요청 처리와 공유하며 replay를 먼저 제한 삭제합니다. */
@Repository
public class VoiceAiRetentionRepository {
    private final JdbcTemplate jdbc;

    public VoiceAiRetentionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> lockEligibleSessions(LocalDateTime cutoff, int limit) {
        return jdbc.queryForList("""
                SELECT s.session_id
                FROM voice_ai_session s
                WHERE EXISTS (
                    SELECT 1 FROM voice_ai_replay r
                    WHERE r.session_id = s.session_id
                      AND ((r.status = 'COMPLETED' AND (r.completed_at IS NULL OR r.completed_at <= ?))
                        OR (r.status = 'PROCESSING' AND r.processing_lease_until IS NULL))
                ) OR (((s.status = 'CLOSED' AND s.closed_at <= ?)
                    OR (s.status = 'ACTIVE' AND s.expires_at <= ?)) AND NOT EXISTS (
                    SELECT 1 FROM voice_ai_replay r
                    WHERE r.session_id = s.session_id
                      AND ((r.status = 'COMPLETED' AND r.completed_at > ?)
                        OR (r.status = 'PROCESSING' AND (r.processing_lease_until IS NULL OR r.processing_lease_until > ?)))
                  ))
                ORDER BY s.session_id
                LIMIT ? FOR UPDATE OF s SKIP LOCKED
                """, String.class, Timestamp.valueOf(cutoff), Timestamp.valueOf(cutoff), Timestamp.valueOf(cutoff),
                Timestamp.valueOf(cutoff), Timestamp.valueOf(cutoff), limit);
    }

    public int initializeLegacyTimestamps(String sessionId, LocalDateTime now, LocalDateTime leaseUntil, int limit) {
        return jdbc.update("""
                UPDATE voice_ai_replay
                SET completed_at = CASE WHEN status = 'COMPLETED' THEN ? ELSE completed_at END,
                    processing_lease_until = CASE WHEN status = 'PROCESSING' THEN ? ELSE processing_lease_until END
                WHERE voice_ai_replay_id IN (
                    SELECT voice_ai_replay_id FROM voice_ai_replay
                    WHERE session_id = ? AND ((status = 'COMPLETED' AND completed_at IS NULL)
                        OR (status = 'PROCESSING' AND processing_lease_until IS NULL))
                    ORDER BY voice_ai_replay_id LIMIT ?
                )
                """, Timestamp.valueOf(now), Timestamp.valueOf(leaseUntil), sessionId, limit);
    }

    public int deleteReplayBatch(String sessionId, LocalDateTime cutoff, int limit) {
        return jdbc.update("""
                DELETE FROM voice_ai_replay
                WHERE voice_ai_replay_id IN (
                    SELECT r.voice_ai_replay_id FROM voice_ai_replay r
                    JOIN voice_ai_session s ON s.session_id = r.session_id
                    WHERE r.session_id = ? AND (
                        (r.status = 'COMPLETED' AND r.completed_at <= ?)
                        OR (r.status = 'PROCESSING'
                            AND ((s.status = 'CLOSED' AND s.closed_at <= ?)
                                OR (s.status = 'ACTIVE' AND s.expires_at <= ?))
                            AND r.processing_lease_until <= ?)
                    )
                    ORDER BY r.voice_ai_replay_id LIMIT ?
                )
                """, sessionId, Timestamp.valueOf(cutoff), Timestamp.valueOf(cutoff), Timestamp.valueOf(cutoff),
                Timestamp.valueOf(cutoff), limit);
    }

    public int deleteEmptySession(String sessionId, LocalDateTime cutoff) {
        return jdbc.update("""
                DELETE FROM voice_ai_session s WHERE s.session_id = ?
                  AND ((s.status = 'CLOSED' AND s.closed_at <= ?)
                    OR (s.status = 'ACTIVE' AND s.expires_at <= ?))
                  AND NOT EXISTS (SELECT 1 FROM voice_ai_replay r WHERE r.session_id = s.session_id)
                """, sessionId, Timestamp.valueOf(cutoff), Timestamp.valueOf(cutoff));
    }
}
