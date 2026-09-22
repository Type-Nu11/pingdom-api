package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import java.time.LocalDateTime;

/**
 * 주변 예약 가능 장소 검색의 입력 집합입니다.
 * page는 1부터 시작하고 반경 단위는 km이며 시간·수량·정렬 기본값은 조회 서비스에서 결정합니다.
 */
public record NearbyReservablePlaceCondition(
        int page,
        int limit,
        double latitude,
        double longitude,
        Double radiusKm,
        LocalDateTime from,
        LocalDateTime to,
        Integer quantity,
        AvailabilityProductType productType,
        String category,
        String touristCategory,
        String sort
) {
}
