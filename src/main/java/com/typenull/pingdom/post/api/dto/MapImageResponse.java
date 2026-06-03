package com.typenull.pingdom.post.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "게시글 처리 응답")
public record MapImageResponse(
        @Schema(description = "대상 게시글 ID", example = "10")
        Long id,
        @Schema(description = "처리 결과 메시지", example = "게시글을 저장했습니다.")
        String message
) {
}
