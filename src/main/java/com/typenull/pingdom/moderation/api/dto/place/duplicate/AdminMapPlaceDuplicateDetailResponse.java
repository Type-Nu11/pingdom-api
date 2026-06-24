package com.typenull.pingdom.moderation.api.dto.place.duplicate;

import java.util.List;

public record AdminMapPlaceDuplicateDetailResponse(
        Long id,
        String name,
        String address,
        String kakaoPlaceId,
        Double latitude,
        Double longitude,
        Long userId,
        String registrant,
        long photoCount,
        List<AdminMapPlaceDuplicateCandidateItem> candidates
) {
}
