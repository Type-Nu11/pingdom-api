package com.typenull.pingdom.place.infrastructure.registration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 승인 장소 미디어 백필의 명시적 활성화와 페이지 크기 설정.
 * 활성화 기본값은 false이며 배치 크기 누락 또는 1~500 범위 밖 값은 100으로 보정.
 */
@ConfigurationProperties(prefix = "place.registration-media-backfill")
public record PlaceRegistrationMediaBackfillProperties(Boolean enabled, Integer batchSize) {

    private static final int DEFAULT_BATCH_SIZE = 100;

    public PlaceRegistrationMediaBackfillProperties {
        enabled = Boolean.TRUE.equals(enabled);
        if (batchSize == null || batchSize < 1 || batchSize > 500) {
            batchSize = DEFAULT_BATCH_SIZE;
        }
    }
}
