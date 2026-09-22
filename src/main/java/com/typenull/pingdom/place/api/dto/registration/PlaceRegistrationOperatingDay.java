package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationOperatingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * 신청서의 요일별 영업 상태와 지역 시각.
 * OPEN은 서로 다른 시작·종료 시각이 필요하며 CLOSED는 시각과 휴게 구간 입력 불가.
 * 휴게 구간 목록의 null은 빈 목록으로 취급하고 구간 관계 검증은 등록 서비스가 담당.
 */
public record PlaceRegistrationOperatingDay(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull PlaceRegistrationOperatingStatus status,
        LocalTime opensAt,
        LocalTime closesAt,
        @Valid List<BreakTime> breakTimes
) {
    public record BreakTime(@NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {}
}
