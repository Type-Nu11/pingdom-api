package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingScheduleInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 정규 영업시간과 날짜별 예외 일정을 전체 교체하는 요청.
 * 각 필드를 생략하거나 null로 보내면 서비스에서 빈 집합으로 해석하므로 기존 해당 일정이 제거됨.
 */
@Schema(description = "Merchant Owner 장소 영업시간 변경 요청")
public record MerchantOwnerOperatingScheduleUpdateRequest(
        Set<@Valid RegularHourRequest> regularHours,
        Set<@Valid OperatingExceptionRequest> exceptions
) {
    public PlaceOperatingScheduleInput toInput() {
        return new PlaceOperatingScheduleInput(
                mapNullable(regularHours, RegularHourRequest::toInput),
                mapNullable(exceptions, OperatingExceptionRequest::toInput)
        );
    }

    // 기존 클라이언트의 OpenAPI 참조명을 유지합니다. Java 관리자 DTO와는 독립적이며,
    // 컴포넌트명 변경은 별도 API 호환 작업에서 다룹니다. 같은 이름의 필드 계약은 일치해야 합니다.
    @Schema(name = "AdminMapPlaceRegularOperatingHourRequest", description = "관리자 요일별 정규 영업 시간대")
    public record RegularHourRequest(
            @NotNull(message = "요일은 필수입니다.")
            @Schema(example = "MONDAY") DayOfWeek dayOfWeek,
            @NotNull(message = "영업 시작 시각은 필수입니다.")
            @Schema(type = "string", format = "time", example = "09:00:00") LocalTime opensAt,
            @NotNull(message = "영업 종료 시각은 필수입니다.")
            @Schema(type = "string", format = "time", example = "18:00:00") LocalTime closesAt
    ) {
        private PlaceOperatingScheduleInput.RegularHour toInput() {
            return new PlaceOperatingScheduleInput.RegularHour(dayOfWeek, opensAt, closesAt);
        }
    }

    @Schema(name = "AdminMapPlaceOperatingExceptionRequest", description = "관리자 특정 날짜 영업 예외")
    public record OperatingExceptionRequest(
            @NotNull(message = "예외 날짜는 필수입니다.")
            @Schema(example = "2026-08-15") LocalDate date,
            @Schema(description = "true이면 종일 휴무이며 hours는 비워야 합니다.", example = "true") boolean closed,
            @Schema(description = "대체 영업 시간대. closed가 false이면 하나 이상 필요합니다.")
            Set<@Valid TimeRangeRequest> hours
    ) {
        private PlaceOperatingScheduleInput.ExceptionDay toInput() {
            return new PlaceOperatingScheduleInput.ExceptionDay(date, closed, mapNullable(hours, TimeRangeRequest::toInput));
        }
    }

    @Schema(name = "AdminMapPlaceOperatingTimeRangeRequest", description = "관리자 장소 운영 시간대")
    public record TimeRangeRequest(
            @NotNull(message = "영업 시작 시각은 필수입니다.")
            @Schema(type = "string", format = "time", example = "09:00:00") LocalTime opensAt,
            @NotNull(message = "영업 종료 시각은 필수입니다.")
            @Schema(type = "string", format = "time", example = "18:00:00") LocalTime closesAt
    ) {
        private PlaceOperatingScheduleInput.TimeRange toInput() {
            return new PlaceOperatingScheduleInput.TimeRange(opensAt, closesAt);
        }
    }

    private static <T, R> Set<R> mapNullable(Set<T> values, Function<T, R> mapper) {
        return values == null ? null : values.stream()
                .map(value -> value == null ? null : mapper.apply(value))
                .collect(Collectors.toSet());
    }
}
