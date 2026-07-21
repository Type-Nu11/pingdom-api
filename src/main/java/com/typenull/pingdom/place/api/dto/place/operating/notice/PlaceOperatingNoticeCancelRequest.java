package com.typenull.pingdom.place.api.dto.place.operating.notice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "상점 운영 상태 공지 취소 요청")
public record PlaceOperatingNoticeCancelRequest(
        @NotBlank
        @Size(max = 500)
        String cancelReason
) {
}
