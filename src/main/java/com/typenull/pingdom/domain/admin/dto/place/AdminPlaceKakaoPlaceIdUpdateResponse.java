package com.typenull.pingdom.domain.admin.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 Kakao place id 수정 응답")
public record AdminPlaceKakaoPlaceIdUpdateResponse(
        @Schema(description = "수정된 장소 ID", example = "1")
        Long placeId,
        @Schema(description = "연결된 Kakao place id", example = "27414316")
        String kakaoPlaceId,
        @Schema(description = "처리 결과 메시지", example = "장소 Kakao place id를 수정했습니다.")
        String message
) {
}
