package com.typenull.pingdom.moderation.api.dto.place.quality;

import jakarta.validation.constraints.Size;

public record AdminMapPlaceKakaoPlaceIdUpdateRequest(
        @Size(max = 50, message = "카카오 장소 ID는 50자 이하여야 합니다.")
        String kakaoPlaceId
) {
}
