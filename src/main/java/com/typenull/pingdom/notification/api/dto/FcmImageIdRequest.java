package com.typenull.pingdom.notification.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "좋아요 알림 전송 요청")
public record FcmImageIdRequest(
        @Schema(description = "좋아요 대상 이미지 ID", example = "10")
        Long imageId
) {
}
