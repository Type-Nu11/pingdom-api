package com.typenull.pingdom.consultation.infrastructure.persistence;

import com.typenull.pingdom.consultation.domain.VoiceAiSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceAiSessionRepository extends JpaRepository<VoiceAiSession, String> {
    Optional<VoiceAiSession> findBySessionIdAndUserId(String sessionId, Long userId);
}
