package com.typenull.pingdom.domain.admin.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 장소 Kakao place id 수정 요청")
public record AdminPlaceKakaoPlaceIdUpdateRequest(
        @NotBlank(message = "kakaoPlaceId는 필수입니다.")
        @Size(max = 50, message = "kakaoPlaceId는 50자 이하여야 합니다.")
        @Schema(description = "연결할 Kakao place id", example = "27414316")
        String kakaoPlaceId
) {
}
