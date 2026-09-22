package com.typenull.pingdom.consultation.infrastructure.persistence;

import com.typenull.pingdom.consultation.domain.VoiceAiReplay;
import com.typenull.pingdom.consultation.domain.VoiceAiReplayStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceAiReplayRepository extends JpaRepository<VoiceAiReplay, Long> {
    Optional<VoiceAiReplay> findBySessionIdAndRequestId(String sessionId, String requestId);

    Optional<VoiceAiReplay> findFirstBySessionIdAndStatusOrderByCreatedAtDesc(String sessionId, VoiceAiReplayStatus status);
}
