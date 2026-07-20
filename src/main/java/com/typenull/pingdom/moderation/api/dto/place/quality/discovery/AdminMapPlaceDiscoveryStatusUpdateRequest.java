package com.typenull.pingdom.moderation.api.dto.place.quality.discovery;

import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 장소 탐색 노출 상태 수정 요청")
public record AdminMapPlaceDiscoveryStatusUpdateRequest(
        @NotNull(message = "탐색 노출 상태는 필수입니다.")
        @Schema(description = "공개 탐색 노출 상태", example = "HIDDEN")
        PlaceDiscoveryStatus discoveryStatus,

        @NotBlank(message = "수정 사유는 필수입니다.")
        @Size(max = 500, message = "수정 사유는 500자 이하여야 합니다.")
        @Schema(description = "감사 로그에 기록할 수정 사유", example = "중복 장소 검수 전 임시 숨김")
        String reason
) {
}
