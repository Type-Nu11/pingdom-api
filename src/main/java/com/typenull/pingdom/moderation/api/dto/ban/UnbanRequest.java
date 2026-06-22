package com.typenull.pingdom.moderation.api.dto.ban;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 밴 해제 요청 정보")
public record UnbanRequest(
        @Schema(description = "밴 해제 사유", example = "운영 검토 결과 해제")
        @Size(max = 255, message = "밴 해제 사유는 255자 이하로 입력해주세요.")
        String reason
) {
}
