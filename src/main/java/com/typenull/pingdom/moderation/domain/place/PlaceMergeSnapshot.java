package com.typenull.pingdom.moderation.domain.place;

import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * 기존 무버전 병합 JSON의 제한된 복구 계약. 누락된 legacy 필드는 null로 읽고 복원 단계에서 기본값 적용.
 * 노출·검증 상태, 생성 시각, 설명·휴게시간·커뮤니티 조회수는 과거 값 복원 대상에 포함하지 않음.
 */
public record PlaceMergeSnapshot(
        Long id,
        String name,
        String address,
        String roadAddress,
        String jibunAddress,
        String postalCode,
        GeocodingSource geocodingSource,
        PlaceOperatingStatus operatingStatus,
        LocalDateTime operatingStatusCheckedAt,
        String category,
        String imageUrl,
        String kakaoPlaceId,
        Double latitude,
        Double longitude,
        Long userId,
        String registrant,
        Long photoCount,
        String englishName,
        String touristSummary,
        Set<TouristCategory> touristCategories,
        List<RegularOperatingHourSnapshot> regularOperatingHours,
        List<OperatingExceptionSnapshot> operatingExceptions
) {
    public record RegularOperatingHourSnapshot(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
    }

    public record OperatingExceptionSnapshot(
            LocalDate date,
            boolean closed,
            List<OperatingTimeRangeSnapshot> hours
    ) {
    }

    public record OperatingTimeRangeSnapshot(LocalTime opensAt, LocalTime closesAt) {
    }
}
