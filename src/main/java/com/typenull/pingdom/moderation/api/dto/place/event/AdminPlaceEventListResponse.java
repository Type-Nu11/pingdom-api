package com.typenull.pingdom.moderation.api.dto.place.event;

import java.util.List;

public record AdminPlaceEventListResponse(
        List<AdminPlaceEventListItem> events, int page, int limit,
        long totalCount, long totalPages, boolean hasNext
) {}
