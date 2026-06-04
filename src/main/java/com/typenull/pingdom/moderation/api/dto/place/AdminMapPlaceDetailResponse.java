package com.typenull.pingdom.moderation.api.dto.place;

import com.typenull.pingdom.place.domain.PlaceGrowthSnapshot;
import java.util.List;
import com.typenull.pingdom.moderation.domain.SortParam;

public record AdminMapPlaceDetailResponse(
        Long id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        Long userId,
        String username,
        SortParam sortParam,
        int postCount,
        PlaceGrowthSnapshot placeGrowth,
        List<AdminMapPlaceImageItem> posts
) {
}
