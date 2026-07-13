package com.typenull.pingdom.moderation.api.dto.place.quality;

import com.typenull.pingdom.place.domain.place.PlaceOperatingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 장소 운영 상태 확인 요청")
public record AdminMapPlaceOperatingStatusUpdateRequest(
        @NotNull(message = "운영 상태는 필수입니다.")
        @Schema(description = "확인된 장소 운영 상태", example = "TEMPORARILY_CLOSED")
        PlaceOperatingStatus operatingStatus,

        @NotBlank(message = "수정 사유는 필수입니다.")
        @Size(max = 500, message = "수정 사유는 500자 이하여야 합니다.")
        @Schema(description = "감사 로그에 기록할 확인 근거", example = "현장 확인 결과 임시 휴업")
        String reason
) {
}
