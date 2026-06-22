package com.typenull.pingdom.moderation.api.dto.ban;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 밴 해제 응답")
public record UnbanResponse(
        @Schema(description = "밴 해제된 사용자 ID", example = "7")
        Long userId,
        @Schema(description = "현재 밴 여부", example = "false")
        boolean banned,
        @Schema(description = "밴 해제 처리 시각", example = "2026-06-22T10:15:30")
        LocalDateTime releasedAt,
        @Schema(description = "밴 해제 사유", example = "운영 검토 결과 해제")
        String reason
) {
}
