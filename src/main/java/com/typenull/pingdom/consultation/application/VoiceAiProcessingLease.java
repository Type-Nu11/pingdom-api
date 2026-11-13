package com.typenull.pingdom.consultation.application;

import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import java.time.Duration;

/** 요청 소유권 인계와 보존 정리가 동일한 provider 처리 기한을 사용합니다. */
final class VoiceAiProcessingLease {
    private VoiceAiProcessingLease() { }

    static Duration duration(GeminiProperties properties) {
        Duration timeout = properties.connectTimeout().plus(properties.readTimeout()).plusSeconds(5);
        return timeout.compareTo(Duration.ofSeconds(30)) < 0 ? Duration.ofSeconds(30) : timeout;
    }
}
