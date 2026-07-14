package com.typenull.pingdom.moderation.api.dto.place.quality.kakao;

public record AdminMapPlaceKakaoPlaceIdUpdateResponse(
        Long placeId,
        String kakaoPlaceId,
        String message
) {
}
