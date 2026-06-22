package com.typenull.pingdom.moderation.api.dto.place;

import java.util.List;

public record AdminMapPlaceDuplicateGroupItem(
        Long representativePlaceId,
        List<Long> duplicatePlaceIds,
        List<String> reasons
) {
}
