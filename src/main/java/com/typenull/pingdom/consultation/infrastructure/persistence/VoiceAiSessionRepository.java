package com.typenull.pingdom.consultation.infrastructure.persistence;

import com.typenull.pingdom.consultation.domain.VoiceAiSession;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceAiSessionRepository extends JpaRepository<VoiceAiSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from VoiceAiSession s where s.sessionId = :sessionId")
    Optional<VoiceAiSession> findByIdForUpdate(@Param("sessionId") String sessionId);

    Optional<VoiceAiSession> findBySessionIdAndUserId(String sessionId, Long userId);
}
