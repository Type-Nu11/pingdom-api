package com.typenull.pingdom.moderation.api.dto.place.duplicate;

import java.util.List;

public record AdminMapPlaceDuplicateResponse(
        List<AdminMapPlaceDuplicateGroupItem> groups,
        int totalCount
) {
}
