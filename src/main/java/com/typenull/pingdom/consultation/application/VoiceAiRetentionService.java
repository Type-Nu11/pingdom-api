package com.typenull.pingdom.consultation.application;

import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiRetentionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceAiRetentionService {
    private final VoiceAiRetentionRepository repository;
    private final VoiceAiRetentionProperties properties;
    private final GeminiProperties geminiProperties;
    private final Clock clock;

    @Autowired
    public VoiceAiRetentionService(VoiceAiRetentionRepository repository, VoiceAiRetentionProperties properties,
            GeminiProperties geminiProperties) {
        // voice_ai의 TIMESTAMP는 기존 세션 서비스와 같은 JVM 로컬 시각으로 해석합니다.
        this(repository, properties, geminiProperties, Clock.systemDefaultZone());
    }

    VoiceAiRetentionService(VoiceAiRetentionRepository repository, VoiceAiRetentionProperties properties,
            GeminiProperties geminiProperties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.geminiProperties = geminiProperties;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public CleanupResult cleanupBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minus(VoiceAiRetentionProperties.RETENTION);
        LocalDateTime legacyLeaseUntil = now.plus(VoiceAiProcessingLease.duration(geminiProperties));
        var sessions = repository.lockEligibleSessions(cutoff, properties.sessionBatchSize());
        int deletedReplays = 0;
        int deletedSessions = 0;
        int initializedTimestamps = 0;
        for (String sessionId : sessions) {
            int remaining = properties.replayBatchSize() - deletedReplays - initializedTimestamps;
            if (remaining == 0) break;
            // 과거 완료 시각은 추정하지 않고 최초 관측 시각부터 14일을 더 보존합니다.
            int initialized = repository.initializeLegacyTimestamps(sessionId, now, legacyLeaseUntil, remaining);
            initializedTimestamps += initialized;
            remaining -= initialized;
            if (remaining > 0) {
                // 부모 잠금 뒤 새 statement에서 완료/lease 시각을 재검사합니다.
                deletedReplays += repository.deleteReplayBatch(sessionId, cutoff, remaining);
            }
            // 자식이 남았으면 부모를 지우지 않아 cascade가 배치 상한을 우회하지 않습니다.
            deletedSessions += repository.deleteEmptySession(sessionId, cutoff);
        }
        return new CleanupResult(deletedSessions, deletedReplays, initializedTimestamps);
    }

    public record CleanupResult(int deletedSessions, int deletedReplays, int initializedTimestamps) {
        public boolean hasProgress() {
            return deletedSessions > 0 || deletedReplays > 0 || initializedTimestamps > 0;
        }
    }
}
