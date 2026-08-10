package com.typenull.pingdom.moderation.api.dto.outbox;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 Outbox 이벤트 재처리 요청")
public record AdminOutboxEventRetryRequest(
        @NotBlank
        @Size(max = 500)
        @Schema(description = "장애 원인 제거와 중복 처리 안전성을 확인한 재처리 사유", example = "외부 메일 공급자 장애 복구 확인")
        String reason
) {
}
