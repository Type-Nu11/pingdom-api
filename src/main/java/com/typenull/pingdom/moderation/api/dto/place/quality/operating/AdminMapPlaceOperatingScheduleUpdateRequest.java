package com.typenull.pingdom.moderation.api.dto.place.quality.operating;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

@Schema(description = "관리자 장소 정규 영업시간과 예외 일정 전체 교체 요청")
public record AdminMapPlaceOperatingScheduleUpdateRequest(
        @Schema(description = "요일별 정규 영업 시간대. 생략하면 모두 제거합니다.")
        Set<@Valid AdminMapPlaceRegularOperatingHourRequest> regularHours,
        @Schema(description = "특정 날짜의 휴무 또는 대체 영업 일정. 생략하면 모두 제거합니다.")
        Set<@Valid AdminMapPlaceOperatingExceptionRequest> exceptions,
        @NotBlank(message = "수정 사유는 필수입니다.")
        @Size(max = 500, message = "수정 사유는 500자 이하여야 합니다.")
        @Schema(description = "감사 로그에 기록할 수정 사유", example = "광복절 휴무와 주말 영업시간 반영")
        String reason
) {
}
