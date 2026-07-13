package com.typenull.pingdom.identity.domain;

/**
 * 관광객이 여행에서 우선하는 목적입니다.
 *
 * <p>현재 값은 장소 관광 카테고리와 맞추되, 사용자 선호와 장소 분류의 변경 책임을 분리합니다.</p>
 */
public enum TravelPurpose {
    K_POP,
    BEAUTY,
    FASHION,
    CAFE,
    FOOD,
    POP_UP,
    EXHIBITION,
    NIGHTLIFE,
    OTHER
}
