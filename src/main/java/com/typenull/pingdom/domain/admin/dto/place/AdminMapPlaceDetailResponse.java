package com.typenull.pingdom.domain.admin.dto.place;

import java.util.List;
import com.typenull.pingdom.domain.admin.enums.SortParam;

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
