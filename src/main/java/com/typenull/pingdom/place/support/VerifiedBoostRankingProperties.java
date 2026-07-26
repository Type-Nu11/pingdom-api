package com.typenull.pingdom.place.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "place.recommendation.verified-boost")
public record VerifiedBoostRankingProperties(double score) {

    private static final double MAX_SCORE = 0.25d;

    public VerifiedBoostRankingProperties {
        if (!Double.isFinite(score) || score < 0d || score > MAX_SCORE) {
            throw new IllegalArgumentException("verified Boost score는 0 이상 0.25 이하여야 합니다.");
        }
    }
}
