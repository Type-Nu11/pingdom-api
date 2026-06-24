package com.typenull.pingdom.moderation.api.dto.place.quality;

public record AdminMapPlaceKakaoPlaceIdUpdateResponse(
        Long placeId,
        String kakaoPlaceId,
        String message
) {
}
