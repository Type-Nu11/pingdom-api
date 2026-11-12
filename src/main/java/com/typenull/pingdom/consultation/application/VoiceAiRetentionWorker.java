package com.typenull.pingdom.consultation.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "voice-ai.retention", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class VoiceAiRetentionWorker {
    private final VoiceAiRetentionService service;
    private final VoiceAiRetentionProperties properties;
    private final MeterRegistry registry;

    @Scheduled(fixedDelayString = "${voice-ai.retention.cleanup-delay:PT1H}",
            initialDelayString = "${voice-ai.retention.cleanup-initial-delay:PT1H}")
    public void cleanupExpiredSessions() {
        long started = System.nanoTime();
        try {
            for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
                var result = service.cleanupBatch();
                // 서비스 프록시가 배치를 커밋한 뒤에만 삭제 건수를 집계합니다.
                registry.counter("pingdom.voice_ai_retention.deleted", "type", "session").increment(result.deletedSessions());
                registry.counter("pingdom.voice_ai_retention.deleted", "type", "replay").increment(result.deletedReplays());
                registry.counter("pingdom.voice_ai_retention.initialized").increment(result.initializedTimestamps());
                if (!result.hasProgress()) break;
            }
            registry.counter("pingdom.voice_ai_retention.cleanup", "result", "success").increment();
        } catch (RuntimeException exception) {
            registry.counter("pingdom.voice_ai_retention.cleanup", "result", "failure").increment();
            // JDBC 예외에는 요청 값이 담길 수 있으므로 원문·SQL 인자·stacktrace를 출력하지 않습니다.
            log.warn("Voice AI retention cleanup failed exceptionType={}", exception.getClass().getSimpleName());
        } finally {
            registry.timer("pingdom.voice_ai_retention.duration").record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }
}
