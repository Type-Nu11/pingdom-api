package com.typenull.pingdom.place.support;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 측정 전 보수적인 기본값이며, 영속 Outbox와 별도로 추천 노출의 동시성과 대기 작업 수를 제한합니다. */
@Validated
@ConfigurationProperties(prefix = "place.recommendation.exposure")
public record PlaceRecommendationExposureProperties(
        @DefaultValue("1") @Min(1) int workerConcurrency,
        @DefaultValue("40") @Min(0) int queueCapacity,
        @DefaultValue("30") @Min(1) int shutdownAwaitSeconds
) {
}
