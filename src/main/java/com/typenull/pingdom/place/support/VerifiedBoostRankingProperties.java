package com.typenull.pingdom.place.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 검증된 프로모션에 더하는 추천 점수.
 * 0은 가점을 없애며 유한한 0~0.25 범위 밖 설정은 시작 시 거절됨.
 */
@ConfigurationProperties(prefix = "place.recommendation.verified-boost")
public record VerifiedBoostRankingProperties(double score) {

    private static final double MAX_SCORE = 0.25d;

    public VerifiedBoostRankingProperties {
        if (!Double.isFinite(score) || score < 0d || score > MAX_SCORE) {
            throw new IllegalArgumentException("verified Boost score는 0 이상 0.25 이하여야 합니다.");
        }
    }
}
