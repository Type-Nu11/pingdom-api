package com.typenull.pingdom.place.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "place.recommendation")
public record PlaceRecommendationProperties(
        String defaultVersion,

        @Valid
        List<VersionPolicy> versions
) {

    public record VersionPolicy(
            String version,

            RecommendationStage stage,

            @Min(value = 0, message = "trafficPercentage는 0 이상이어야 합니다.")
            @Max(value = 100, message = "trafficPercentage는 100 이하여야 합니다.")
            int trafficPercentage,

            boolean featureLoggingEnabled,

            @Min(value = 1, message = "portfolioSizeMultiplier는 1 이상이어야 합니다.")
            int portfolioSizeMultiplier,

            @DecimalMin(value = "0.0", message = "mmrRelevanceWeight는 0 이상이어야 합니다.")
            @DecimalMax(value = "1.0", message = "mmrRelevanceWeight는 1 이하여야 합니다.")
            double mmrRelevanceWeight,

            @Valid
            CandidateMix mix,

            @Valid
            RankingWeights personalizedWeights,

            @Valid
            RankingWeights anonymousWeights
    ) {
    }

    public record CandidateMix(
            @DecimalMin(value = "0.0", message = "personalRatio는 0 이상이어야 합니다.")
            @DecimalMax(value = "1.0", message = "personalRatio는 1 이하여야 합니다.")
            double personalRatio,

            @DecimalMin(value = "0.0", message = "popularRatio는 0 이상이어야 합니다.")
            @DecimalMax(value = "1.0", message = "popularRatio는 1 이하여야 합니다.")
            double popularRatio,

            @DecimalMin(value = "0.0", message = "freshRatio는 0 이상이어야 합니다.")
            @DecimalMax(value = "1.0", message = "freshRatio는 1 이하여야 합니다.")
            double freshRatio,

            @DecimalMin(value = "0.0", message = "geoRatio는 0 이상이어야 합니다.")
            @DecimalMax(value = "1.0", message = "geoRatio는 1 이하여야 합니다.")
            double geoRatio
    ) {
    }

    public record RankingWeights(
            @DecimalMin(value = "0.0", message = "geoWeight는 0 이상이어야 합니다.")
            double geoWeight,

            @DecimalMin(value = "0.0", message = "personalWeight는 0 이상이어야 합니다.")
            double personalWeight,

            @DecimalMin(value = "0.0", message = "qualityWeight는 0 이상이어야 합니다.")
            double qualityWeight,

            @DecimalMin(value = "0.0", message = "engagementWeight는 0 이상이어야 합니다.")
            double engagementWeight,

            @DecimalMin(value = "0.0", message = "conversionWeight는 0 이상이어야 합니다.")
            double conversionWeight,

            @DecimalMin(value = "0.0", message = "freshnessWeight는 0 이상이어야 합니다.")
            double freshnessWeight,

            @DecimalMin(value = "0.0", message = "explorationWeight는 0 이상이어야 합니다.")
            double explorationWeight
    ) {
    }

    public enum RecommendationStage {
        STABLE,
        EXPERIMENTAL
    }
}
