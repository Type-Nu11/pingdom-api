package com.typenull.pingdom.place.api.dto.place.operating.notice;

import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "상점 운영 상태 공지 수정 요청")
public record PlaceOperatingNoticeUpdateRequest(
        @NotNull
        PlaceOperatingNoticeSeverity severity,
        @NotBlank
        @Size(max = 500)
        String message
) {
}
