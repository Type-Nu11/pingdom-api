package com.typenull.pingdom.moderation.api.dto.ad;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record AdminAdCreateRequest(
        @Schema(description = "이벤트/광고 제목", example = "여름 한정 출석 이벤트")
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        String title,

        @Schema(description = "배너 이미지 URL", example = "https://cdn.pingdom.com/banner/summer-event.png")
        @NotBlank(message = "이미지 URL은 필수입니다.")
        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        String imageUrl,

        @Schema(description = "클릭 시 이동할 URL", example = "https://pingdom.com/events/summer")
        @NotBlank(message = "이동 URL은 필수입니다.")
        @Size(max = 500, message = "이동 URL은 500자 이하여야 합니다.")
        String redirectUrl,

        @Schema(description = "노출 시작 시각", example = "2026-06-20T09:00:00")
        @NotNull(message = "시작 시각은 필수입니다.")
        LocalDateTime startAt,

        @Schema(description = "노출 종료 시각", example = "2026-06-30T23:59:59")
        @NotNull(message = "종료 시각은 필수입니다.")
        LocalDateTime endAt
) {
}
