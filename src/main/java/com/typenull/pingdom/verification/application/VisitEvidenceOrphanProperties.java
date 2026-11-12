package com.typenull.pingdom.verification.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "verification.visit-evidence-orphan")
public record VisitEvidenceOrphanProperties(Duration uploadTimeout, Integer batchSize, Duration recheckDelay) {
    public VisitEvidenceOrphanProperties {
        uploadTimeout = uploadTimeout == null ? Duration.ofMinutes(2) : uploadTimeout;
        batchSize = batchSize == null ? 50 : batchSize;
        recheckDelay = recheckDelay == null ? Duration.ofDays(1) : recheckDelay;
        if (uploadTimeout.isZero() || uploadTimeout.isNegative() || uploadTimeout.compareTo(Duration.ofMinutes(5)) > 0
                || batchSize < 1 || batchSize > 100 || recheckDelay.isZero() || recheckDelay.isNegative()) {
            throw new IllegalArgumentException("증빙 orphan 정리 설정이 올바르지 않습니다.");
        }
    }
}
