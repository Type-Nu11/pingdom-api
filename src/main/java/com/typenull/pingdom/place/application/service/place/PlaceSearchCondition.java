package com.typenull.pingdom.place.application.service.place;

/**
 * 일반 장소 검색의 페이지·분류·위치 조건.
 * 위도·경도·km 반경은 서비스에서 함께 전달된 경우에만 위치 검색으로 해석.
 */
public record PlaceSearchCondition(
        int page,
        int limit,
        String keyword,
        String category,
        String touristCategory,
        Double latitude,
        Double longitude,
        Double radiusKm,
        String sort
) {
}
