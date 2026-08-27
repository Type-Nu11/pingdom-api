package com.typenull.pingdom.place.infrastructure.localhot;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
