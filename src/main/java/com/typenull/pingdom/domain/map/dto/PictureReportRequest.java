package com.typenull.pingdom.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "사진 신고 요청 정보")
public record PictureReportRequest(
        @NotBlank(message = "신고 사유는 필수입니다.")
        @Size(max = 500, message = "신고 사유는 500자 이하여야 합니다.")
        @Schema(description = "사진 신고 사유", example = "부적절한 이미지입니다.")
        String reason
) {
}
