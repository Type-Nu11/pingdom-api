package com.typenull.pingdom.moderation.api.dto.place.duplicate;

public record AdminMapPlaceDuplicateCandidateItem(
        Long id,
        String name,
        String address,
        String kakaoPlaceId,
        Double latitude,
        Double longitude,
        Long userId,
        String registrant,
        long photoCount,
        String reason,
        Double distanceMeters
) {
}
