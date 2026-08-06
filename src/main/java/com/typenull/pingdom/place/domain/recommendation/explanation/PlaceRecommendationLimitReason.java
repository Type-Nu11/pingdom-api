package com.typenull.pingdom.place.domain.recommendation.explanation;

/** 요청 조건과 후보 제한으로 추천 결과에 영향을 준 대표 사유 코드. */
public enum PlaceRecommendationLimitReason {
    REQUEST_LIMIT_CLAMPED,
    RADIUS_EXPANDED,
    OPERATING_STATUS_PRIORITY,
    INTERACTED_PLACE_EXCLUDED,
    FALLBACK_CANDIDATE_POOL
}
