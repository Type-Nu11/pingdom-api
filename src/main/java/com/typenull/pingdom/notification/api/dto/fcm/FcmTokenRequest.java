package com.typenull.pingdom.notification.api.dto.fcm;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FCM 토큰 업데이트 요청")
public record FcmTokenRequest(
        @Schema(description = "최신 기기 FCM 토큰", example = "fcm-device-token-sample")
        String token
) {
}
