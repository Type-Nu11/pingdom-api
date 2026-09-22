package com.typenull.pingdom.place.infrastructure.localhot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 행정구역 미지정 장소의 시작 시 보강 작업 설정.
 * 기본은 비활성·100개이고 배치 크기가 1~500 범위를 벗어나면 100개로 되돌림.
 */
@ConfigurationProperties(prefix = "place.local-hot.backfill")
public record PlaceAdministrativeRegionBackfillProperties(Boolean enabled, Integer batchSize) {

    private static final int DEFAULT_BATCH_SIZE = 100;

    public PlaceAdministrativeRegionBackfillProperties {
        enabled = Boolean.TRUE.equals(enabled);
        if (batchSize == null || batchSize < 1 || batchSize > 500) {
            batchSize = DEFAULT_BATCH_SIZE;
        }
    }
}
