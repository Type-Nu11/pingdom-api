package com.typenull.pingdom.moderation.api.dto.ban;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Schema(description = "관리자 밴 처리 요청 정보")
public record BanRequest(
        @Schema(description = "밴 사유", example = "반복적인 신고 누적")
        @Size(max = 255, message = "밴 사유는 255자 이하로 입력해주세요.")
        String reason,
        @Schema(description = "기간 밴 종료 시각. 비우면 영구 밴으로 처리됩니다.", example = "2026-06-30T23:59:59")
        LocalDateTime expiresAt,
        @Schema(description = "현재 시각부터 적용할 밴 기간(일). expiresAt과 동시에 사용할 수 없습니다.", example = "7")
        @Min(value = 1, message = "밴 기간은 1일 이상이어야 합니다.")
        Long durationDays
) {
}
