package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.*;
import java.time.*;

/**
 * 체크인의 서울 기준 날짜와 클라이언트 관측/서버 기록 시각을 구분해 반환.
 * 거리는 미터이며 PROXIMITY_MATCHED와 DWELL_VERIFIED는 인증 수준이 다름.
 */
public record LocationCheckInResponse(Long id, Long placeId, LocalDate checkInDate, Instant observedAt,
        Instant recordedAt, double distanceMeters,
        LocationCheckInStatus status) {
    public static LocationCheckInResponse from(LocationCheckIn checkIn) {
        return new LocationCheckInResponse(checkIn.getId(), checkIn.getPlaceId(), checkIn.getCheckInDate(),
                checkIn.getObservedAt(), checkIn.getRecordedAt(), checkIn.getDistanceMeters(), checkIn.getStatus());
    }
}
