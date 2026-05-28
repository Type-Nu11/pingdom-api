package com.typenull.pingdom.domain.admin.dto.place;

import java.util.List;

public record AdminMapPlaceDetailResponse(
        Long id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        Long userId,
        int postCount,
        List<AdminMapPlaceImageItem> posts
) {
}
