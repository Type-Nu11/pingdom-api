package com.typenull.pingdom.moderation.api.dto.place.event;

import com.typenull.pingdom.place.domain.event.PlaceEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Schema(description = "관리자 기간형 이벤트 등록·수정 요청")
public record AdminPlaceEventRequest(
        @Schema(description = "행사 장소 ID", example = "17")
        @NotNull(message = "장소 ID는 필수입니다.")
        Long placeId,

        @Schema(description = "이벤트 제목", example = "진주 여름 빛 축제")
        @NotBlank(message = "이벤트 제목은 필수입니다.")
        @Size(max = 150, message = "이벤트 제목은 150자 이하여야 합니다.")
        String title,

        @Schema(description = "이벤트 설명", example = "남강을 배경으로 열리는 야간 공연과 전시입니다.")
        @Size(max = 1000, message = "이벤트 설명은 1000자 이하여야 합니다.")
        String description,

        @Schema(description = "이벤트 유형", example = "EXHIBITION")
        @NotNull(message = "이벤트 유형은 필수입니다.")
        PlaceEventType eventType,

        @Schema(description = "시작 시각", example = "2026-08-01T10:00:00")
        @NotNull(message = "시작 시각은 필수입니다.")
        LocalDateTime startAt,

        @Schema(description = "종료 시각", example = "2026-08-31T20:00:00")
        @NotNull(message = "종료 시각은 필수입니다.")
        LocalDateTime endAt,

        @Schema(description = "관리자 변경 사유", example = "공식 일정 등록")
        @NotBlank(message = "변경 사유는 필수입니다.")
        @Size(max = 500, message = "변경 사유는 500자 이하여야 합니다.")
        String reason
) {
}
