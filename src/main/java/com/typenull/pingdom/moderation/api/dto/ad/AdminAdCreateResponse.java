package com.typenull.pingdom.moderation.api.dto.ad;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AdminAdCreateResponse(
        @Schema(description = "생성된 이벤트/광고 ID", example = "1")
        Long adId,

        @Schema(description = "이벤트/광고 제목", example = "여름 한정 출석 이벤트")
        String title,

        @Schema(description = "노출 시작 시각", example = "2026-06-20T09:00:00")
        LocalDateTime startAt,

        @Schema(description = "노출 종료 시각", example = "2026-06-30T23:59:59")
        LocalDateTime endAt,

        @Schema(description = "처리 결과 메시지", example = "이벤트/광고를 등록했습니다.")
        String message
) {
}
