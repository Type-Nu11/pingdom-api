package com.typenull.pingdom.domain.map.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지도 이미지 처리 응답")
public record MapImageResponse(
        @Schema(description = "대상 이미지 ID", example = "10")
        Long id,
        @Schema(description = "처리 결과 메시지", example = "사진을 저장했습니다.")
        String message
) {
}
