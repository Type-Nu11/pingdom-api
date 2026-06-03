package com.typenull.pingdom.engagement.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "게시글 신고 요청 정보")
public record PostReportRequest(
        @NotBlank(message = "신고 사유는 필수입니다.")
        @Size(max = 500, message = "신고 사유는 500자 이하여야 합니다.")
        @Schema(description = "게시글 신고 사유", example = "부적절한 게시글입니다.")
        String reason
) {
}
