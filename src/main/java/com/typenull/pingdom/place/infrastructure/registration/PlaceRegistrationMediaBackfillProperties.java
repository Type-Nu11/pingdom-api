package com.typenull.pingdom.place.infrastructure.registration;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
