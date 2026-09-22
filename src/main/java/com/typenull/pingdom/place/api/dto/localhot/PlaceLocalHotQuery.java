package com.typenull.pingdom.place.api.dto.localhot;

/**
 * 지역 인기 장소 검색의 원본 입력.
 * 좌표 한 쌍과 5자리 지역 코드 중 하나만 허용하는 조합 검증은 조회 서비스가 수행.
 */
public record PlaceLocalHotQuery(
        Double latitude,
        Double longitude,
        String regionCode,
        int page,
        int limit
) {
}
