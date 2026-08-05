package com.typenull.pingdom.place.domain.recommendation.explanation;

/** 추천 결과가 선택된 대표 근거 코드. 표시 문구는 API 계층에서 결정한다. */
public enum PlaceRecommendationReason {
    BENEFIT_AND_RESERVABLE,
    ACTIVE_BENEFIT,
    RESERVABLE,
    CONTEXT_MATCH,
    PERSONAL_SIGNAL,
    FRESH_CONTENT,
    HIGH_ENGAGEMENT,
    HIGH_CONVERSION,
    EXPLORATION,
    QUALITY_SIGNAL,
    NEARBY
}
