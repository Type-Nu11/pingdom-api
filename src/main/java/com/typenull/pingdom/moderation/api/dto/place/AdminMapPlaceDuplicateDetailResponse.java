package com.typenull.pingdom.moderation.api.dto.place;

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
