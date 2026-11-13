package com.typenull.pingdom.identity.application.command;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.profile-image-storage")
public record ProfileImageStorageProperties(Duration uploadTimeout, Integer batchSize, Duration recheckDelay) {
    public ProfileImageStorageProperties {
        uploadTimeout = uploadTimeout == null ? Duration.ofMinutes(2) : uploadTimeout;
        batchSize = batchSize == null ? 50 : batchSize;
        recheckDelay = recheckDelay == null ? Duration.ofDays(1) : recheckDelay;
        if (uploadTimeout.isZero() || uploadTimeout.isNegative() || uploadTimeout.compareTo(Duration.ofMinutes(5)) > 0
                || batchSize < 1 || batchSize > 100 || recheckDelay.isZero() || recheckDelay.isNegative())
            throw new IllegalArgumentException("프로필 이미지 정리 설정이 올바르지 않습니다.");
    }
}
