package com.typenull.pingdom.place.api.dto.place.information.reverification;

import java.util.List;

public record PlaceInformationReverificationListResponse(
        List<PlaceInformationReverificationResponse> requests,
        int page, int limit, long totalCount, int totalPages, boolean hasNext
) {}
