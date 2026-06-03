package com.typenull.pingdom.moderation.api.dto.place;

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
        List<AdminMapPlaceImageItem> posts
) {
}
